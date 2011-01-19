(**** beginning of start.txt ****)
(This file is for a MakerBot Thing-O-Matic with)
(an automated build platform)
(This file has been sliced using Skeinforge 35)
(**** begin initilization commands ****)
G21 (set units to mm)
G90 (set positioning to absolute)
M108 R1.98 (set extruder speed)
M104 S225 T0 (set extruder temperature)
M109 S125 T0 (set heated-build-platform temperature)
(**** end initilization commands ****)
(**** begin homing ****)
G162 Z F500 (home Z axis maximum)
G161 X Y F2500 (home XY axes minimum)
G92 Z80 ( ---=== Set Z axis maximum ===--- )
G92 X-57.5 Y-57 (set zero for X and Y)
(**** end homing ****)
(**** begin pre-wipe commands ****)
M103 (Make sure extruder is off)
G1 X52 Y-57.0 Z10 F3300.0 (move to waiting position)
M6 T0 (wait for toolhead parts, nozzle, HBP, etc., to reach temperature)
(**** end pre-wipe commands ****)
(**** end of start.txt ****)

