package replicatorg.app.ui;
/*
Part of the ReplicatorG project - http://www.replicat.org
Copyright (c) 2008 Zach Smith

Forked from Arduino: http://www.arduino.cc

Based on Processing http://www.processing.org
Copyright (c) 2004-05 Ben Fry and Casey Reas
Copyright (c) 2001-04 Massachusetts Institute of Technology

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

$Id: MainWindow.java 370 2008-01-19 16:37:19Z mellis $
 */
/**
 * @author Noah Levy
 * 
 * <class>DualStrusionWindow</class> is a Swing class designed to integrate DualStrusion into the existing ReplicatorG GUI
 */

/**
 * 
 */
import java.awt.Container;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.gcode.DualStrusionConstruction;
import replicatorg.app.gcode.GCodeHelper;
import replicatorg.app.gcode.MutableGCodeSource;
import replicatorg.machine.model.MachineType;
import replicatorg.machine.model.ToolheadAlias;
import replicatorg.model.Build;
import replicatorg.model.GCodeSource;
import replicatorg.plugin.toolpath.ToolpathGenerator;
import replicatorg.plugin.toolpath.ToolpathGenerator.GeneratorEvent;
import replicatorg.plugin.toolpath.ToolpathGenerator.GeneratorListener;
import replicatorg.plugin.toolpath.ToolpathGeneratorFactory;
import replicatorg.plugin.toolpath.ToolpathGeneratorThread;
import replicatorg.plugin.toolpath.skeinforge.SkeinforgeGenerator;
import replicatorg.plugin.toolpath.skeinforge.SkeinforgePostProcessor;


/**
 * This is the window that shows you Dualstrusion options, and also prepares everything for combination
 * I'd like to improve this in the future, adapting it prepare build plates, etc.
 * 
 * Also, because this is still very new (and potentially buggy) code, I've thrown a whole lot of logging calls in,
 * Those can be removed in a future release.
 */

/*
 * NOTE: to self(Ted):
 * to test: generate each individual, generate merged, merge individual for each dualstrusion thing that we have

   We can have a more reliable thing that combines STLs, and a less reliable thing that combines gcodes
   (Start code removal without getting it straight from skeinforge is a little less certain)

   We should set up the post-processor before starting the generator, and the generator should just call the post-processor itself
 */
public class DualStrusionWindow extends JFrame{
	private static final long serialVersionUID = 2548421042732389328L; //Generated serial

	// why is there a dest and a result?
	File dest;

	boolean uWipe;
	File leftStl = null;
	File rightStl = null;
	File leftGcode = null;
	File rightGcode = null;
	MutableGCodeSource leftSource;
	MutableGCodeSource rightSource;
	MutableGCodeSource startSource;
	MutableGCodeSource endSource;

	boolean aborted = false;
	CountDownLatch completed;
	
	final MachineType type;
	/**
	 * 
	 * This is the default constructor, it is only invoked if the ReplicatorG window did not already have a piece of gcode open
	 */
	public DualStrusionWindow(MachineType type, MutableGCodeSource startCode, MutableGCodeSource endCode)
	{
		this(type, startCode, endCode, null);
	}
	
	/**
	 * This method creates and shows the DualStrusionWindow GUI, this window is a MigLayout with 3 JFileChooser-TextBox Pairs, the first two being source gcodes and the last being the combined gcode destination.
	 * It also links to online DualStrusion Documentation NOTE: This may be buggy, it uses getDesktop() which is JDK 1.6 and scary.
	 * This method also invokes the thread in which the gcode combining operations run in, I would like to turn this into a SwingWorker soon.

	 * This is a constructor that takes the filepath of the gcode open currently in ReplicatorG
	 * @param s the path of the gcode currently open in RepG
	 */
	
	/*
	 * 
	 */
	
	public DualStrusionWindow(MachineType type, MutableGCodeSource startCode, MutableGCodeSource endCode, String path) {
		super("DualStrusion Window (EXPERIMENTAL functionality)");

		Base.logger.log(Level.FINE, "Dualstrusion window booting up...");
		
		startSource = startCode;
		endSource = endCode;
		
		setResizable(true);
		setLocation(400, 0);
		Container cont = this.getContentPane();
		cont.setLayout(new MigLayout("fill"));
		//put this in a label or something
		//"This window is used to combine two Gcode files generated by SkeinForge. 
		//This allows for multiple objects in one print job or multiple materials 
		//or colors in one printed object."
		this.type = type;
		
		cont.add(new JLabel("Left Extruder"), "split");

		final JTextField leftToolhead = new JTextField(60);
		String loadFileName = Base.preferences.get("dualstrusionwindow.leftfile", path);
		if(loadFileName != null)
			leftToolhead.setText(loadFileName);
		else
			leftToolhead.setText("");
		JButton leftChooserButton = new JButton("Browse...");
		leftChooserButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				String s = null;
				if(!leftToolhead.getText().equals(""))
				{
					s = GcodeSelectWindow.goString(new File(leftToolhead.getText()));	
				}
				else
				{
					s = GcodeSelectWindow.goString();
				}
				if(s != null)
				{
					leftToolhead.setText(s);
				}
			}
		});
		cont.add(leftToolhead,"split");
		cont.add(leftChooserButton, "wrap");

		final JTextField rightToolhead = new JTextField(60);
		loadFileName = Base.preferences.get("dualstrusionwindow.rightfile", path);
		if(loadFileName != null)
			rightToolhead.setText(loadFileName);
		else
			rightToolhead.setText("");

		JButton rightChooserButton = new JButton("Browse...");
		rightChooserButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				String s = null;
				if(!rightToolhead.getText().equals(""))
				{
					s = GcodeSelectWindow.goString(new File(rightToolhead.getText()));	
				}
				else
				{
					s = GcodeSelectWindow.goString();
				}
				if(s != null)
				{
					rightToolhead.setText(s);
				}
			}
		});
		JButton switchItem = new JButton("Switch Toolheads"); //This button switches the contents of the two text fields in order to easily swap Primary and Secondary Toolheads
		switchItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				String temp = leftToolhead.getText();
				leftToolhead.setText(rightToolhead.getText());
				rightToolhead.setText(temp);

			}
		});
		cont.add(switchItem, "wrap");
		cont.add(new JLabel("Right Extruder"), "split");

		cont.add(rightToolhead,"split");
		cont.add(rightChooserButton, "wrap");

		final JTextField DestinationTextField = new JTextField(60);
		loadFileName = Base.preferences.get("dualstrusionwindow.destfile", "");
		DestinationTextField.setText(loadFileName);

		JButton DestinationChooserButton = new JButton("Browse...");
		DestinationChooserButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				String s = null;
				if(!DestinationTextField.getText().equals(""))
				{
					s = GcodeSaveWindow.goString(new File(DestinationTextField.getText()));	
				}
				else
				{
					if(!leftToolhead.getText().equals(""))
					{
						int i = leftToolhead.getText().lastIndexOf("/");
						String a = leftToolhead.getText().substring(0, i + 1) + "untitled.gcode";
						File untitled = new File(a);
						System.out.println(a);
						s = GcodeSaveWindow.goString(untitled);
					}	
					else
					{
						s = GcodeSaveWindow.goString();
					}
				}

				if(s != null)
				{
					if(s.contains("."))
					{
						int lastp = s.lastIndexOf(".");
						if(!s.substring(lastp + 1, s.length()).equals("gcode"))
						{
							s = s.substring(0, lastp) + ".gcode";
						}
					}
					else
					{
						s = s + ".gcode";
					}
					DestinationTextField.setText(s);
				}

			}

		});
		cont.add(new JLabel("Save As: "), "split");
		cont.add(DestinationTextField, "split");
		cont.add(DestinationChooserButton, "wrap");
		
		//Use Wipes	
		final JCheckBox useWipes = new JCheckBox();
		useWipes.setSelected(true);
		cont.add(new JLabel("Use wipes defined in machines.xml"), "split");
		cont.add(useWipes, "wrap");
		
		//Merge
		JButton merge = new JButton("Merge");

		merge.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(leftToolhead.getText().equals(rightToolhead.getText()))
				{
					int option = JOptionPane.showConfirmDialog(null, "You are trying to combine two of the same file. Are you sure you want to do this?",
							"Continue?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					System.out.println(option);
					if(option == 1)
						return;
				}
				
				File test = new File(leftToolhead.getText());
				if(test.exists())
				{
					String ext = getExtension(test.getName());
					if("stl".equalsIgnoreCase(ext))
						leftStl = test;
					else if("gcode".equalsIgnoreCase(ext))
						leftGcode = test;
					else
					{
						JOptionPane.showConfirmDialog(null, "File for Extruder A not an stl or gcode. Please select something I can understand.",
								"Select a different file.", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
						return;
					}
				}
				
				test = new File(rightToolhead.getText());
				if(test.exists())
				{
					String ext = getExtension(test.getName());
					if("stl".equalsIgnoreCase(ext))
						rightStl = test;
					else if("gcode".equalsIgnoreCase(ext))
						rightGcode = test;
					else
					{
						JOptionPane.showConfirmDialog(null, "File for Extruder B not an stl or gcode. Please select something I can understand.",
								"Select a different file.", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
						return;
					}
				}
				
				// Let's record the files and destination so they don't need to be entered every time
				Base.preferences.put("dualstrusionwindow.leftfile", leftToolhead.getText());
				Base.preferences.put("dualstrusionwindow.rightfile", rightToolhead.getText());
				Base.preferences.put("dualstrusionwindow.destfile", DestinationTextField.getText());
				
				dest = new File(DestinationTextField.getText());
				
				uWipe = useWipes.isSelected();

				// completed counts the number of stls left to convert 
				completed = new CountDownLatch(2);
				if(leftGcode != null)
				{
					leftSource = new MutableGCodeSource(leftGcode);
					leftSource.stripStartEndBestEffort();
					completed.countDown();
				}
				if(rightGcode != null)
				{
					rightSource = new MutableGCodeSource(rightGcode);
					rightSource.stripStartEndBestEffort();
					completed.countDown();
				}
				if(completed.getCount() == 0)
				{
					combineGcodes();
				}

				if(leftStl != null)
				{
					leftGcode = new File(replaceExtension(leftStl.getAbsolutePath(), "gcode"));
					stlToGcode(leftStl, leftGcode, ToolheadAlias.LEFT, DualStrusionWindow.this.type);
				}
				if(rightStl != null)
				{
					rightGcode = new File(replaceExtension(rightStl.getAbsolutePath(), "gcode"));
					stlToGcode(rightStl, rightGcode, ToolheadAlias.RIGHT, DualStrusionWindow.this.type);
				}
			}

		});
		cont.add(merge, "split");
		final JButton help = new JButton("Help");
		help.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				try {
					Desktop.getDesktop().browse(new URI("http://goo.gl/DV5vn"));
					//That goo.gl redirects to http://www.makerbot.com/docs/dualstrusion I just wanted to build in a convenient to track how many press the help button
				} catch (Exception e)
				{
					Base.logger.log(Level.WARNING, "Could not load online help! See log level FINEST for more details");
					Base.logger.log(Level.FINEST, ""+e);
				}
			}
		});
		cont.add(help);
		pack();

		Base.logger.log(Level.FINE, "Finishing construction of Dualstrusion window");
	}
	
	private void stlToGcode(File stl, File gcode, ToolheadAlias tool, MachineType type)
	{
		try{
			if(!gcode.exists())
				gcode.createNewFile();

			System.out.println(gcode.getName());

			String title = "STL to GCode ";
			if(tool == ToolheadAlias.LEFT)
				title += "(Left) ";
			if(tool == ToolheadAlias.RIGHT)
				title += "(Right) ";
			title += "Progress";
			
			final JFrame progress = new JFrame(title);
			final ToolpathGenerator gen = ToolpathGeneratorFactory.createSelectedGenerator();

			if(gen instanceof SkeinforgeGenerator)
			{
				// Here we'll do the setup for the post-processor
				//Let's figure out which post-processing steps need to be taken
				Set<String> postProcessingSteps = new TreeSet<String>();
				
				postProcessingSteps.add(SkeinforgePostProcessor.TARGET_TOOLHEAD_DUAL);

				if(type == MachineType.THE_REPLICATOR)
					postProcessingSteps.add(SkeinforgePostProcessor.MACHINE_TYPE_REPLICATOR);
				else if(type == MachineType.THINGOMATIC)
					postProcessingSteps.add(SkeinforgePostProcessor.MACHINE_TYPE_TOM);
				else if(type == MachineType.CUPCAKE)
					postProcessingSteps.add(SkeinforgePostProcessor.MACHINE_TYPE_CUPCAKE);
				
				((SkeinforgeGenerator) gen).setPostProcessor(new SkeinforgePostProcessor(
								((SkeinforgeGenerator) gen), null, null, postProcessingSteps));
				
			}
			final Build b = new Build(stl.getAbsolutePath());
			final ToolpathGeneratorThread tgt = new ToolpathGeneratorThread(progress, gen, b);

			tgt.addListener(new GeneratorListener(){
				@Override
				public void updateGenerator(GeneratorEvent evt) {
//					if(evt.getMessage().equals("Config Done") && !stls.isEmpty())
//					{
//						Base.logger.log(Level.FINE, "Starting next stl > gcode: " + stls.peek().getName());
//						stlsToGcode();
//					}
				}

				@Override
				public void generationComplete(GeneratorEvent evt) {
					if(evt.getCompletion() == Completion.FAILURE || aborted)
					{
						aborted = true;
						return;
					}
					
					completed.countDown();
					if(completed.getCount() == 0)
					{
						combineGcodes();
					}
				}
				
			});
			tgt.setDualStrusionSupportFlag(true, 200, 300, stl.getName());

			Base.logger.log(Level.FINE, "Init finished, starting conversion");
			
			tgt.start();
		}
		catch(IOException e)
		{
			Base.logger.log(Level.SEVERE, "cannot read stl! Aborting dualstrusion generation, see log level FINEST for more details.");
			Base.logger.log(Level.FINEST, "", e);
			
		} 
	}
	
	private static String getExtension(String path)
	{
		int i = path.lastIndexOf(".");
		String ext = path.substring(i+1, path.length());
		return ext;
	}
	
	private static String replaceExtension(String s, String newExtension)
	{
		int i = s.lastIndexOf(".");
		s = s.substring(0, i+1);
		s = s + newExtension;
		return s;
	}
	
	private void combineGcodes()
	{
		System.out.println(leftGcode == null);
		System.out.println(rightGcode == null);
		//For now this should always be exactly two gcodes, let's just check that assumption
		if(leftGcode == null || rightGcode == null)
		{
			Base.logger.log(Level.SEVERE, "Expected two gcode files after conversion from stl. " +
					"One or both is/are absent. Cancelling Dualstrusion combination");
			return;
		}
System.out.print("reading in generated gcode");
		leftSource = new MutableGCodeSource(leftGcode);
		rightSource = new MutableGCodeSource(rightGcode);
System.out.print("done reading generated gcode");
		
		// the two consecutive poll()s pull what are the only two gcode files
		DualStrusionConstruction dcs = new DualStrusionConstruction(leftSource, rightSource, startSource, endSource, type, uWipe);
System.out.print("called dcs.combine");
		dcs.combine();
System.out.print("dcs.combine returned");
		dcs.getCombinedFile().writeToFile(dest);
		
		Base.logger.log(Level.FINE, "Finished DualStrusionWindow's part");
		
		removeAll();
		dispose();
		
	}
	/**
	 * This method returns the result of the gcode combining operation.
	 * @return the combined gcode.
	 */

	public File getCombined()
	{
		return dest;
	}

}
