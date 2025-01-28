




### Node layout:

Type: 4bits\

types:
- 0000: Constant
- 0001: UserInput
- 0010: Repeater
- 0011: Comparator

#### Data even ticks:
Do update: 1bit\
NodeData: 13bits\
#### Data odd ticks:
Do update: 1bit\
NodeData: 13bits\


#### Input bit layout
Redstone dist: 4bits\
Is side input: 1bit\
Is last input: 1bit\
Last 26 bits: Node idx



#### Arrays:
Node array: [Node, InputPointer, Node, InputPointer, ..]\
Input array: [Node0Input0, Node0Input1, .., Node0InputLast, Node1Input0, ..]


#### Later:
The goal is to first see if having the inputs in a separate array is better,
as well as testing having both states of the node in the same uint.
And then to have inputs and outputs in this array and try the update bit
implementation out.