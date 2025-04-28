

### Data layout
The graph is an array of u32 with nodes, and then
a variable length array of their inputs:\
[u32]
[Node, [Inputs..], Node, [Inputs..]]

Node:\
0000 -> First 4 bits is the node type\
0000 00000000 -> Next 12 bits are the node data\
00000000 00000000 -> Last 16 bits are the amount
of Inputs this node has.\

Input:\
0000 -> First 4 bits is the redstone distance from the other component
(amount to subtract from ss, 16+ is no connection and shouldn't even be in
the input list)\
0 -> Next bit is whether the input is side or back
00 00000000 00000000 00000000 -> Last 27 bits are the index
in the array of the input's node



#### Nodes:(format: BitPattern: NodeName { dataName[bitCount] }, little endian)
**0000**: ConstantSSInput { ss[4bits] }\
Represents an input that's constantly powered\
**0001**: RepeaterNode { locked[1bit], delay[2bits], scheduler[4bits] }\
**0010**: TorchNode { lit[1bit] }\
**0011**: ComparatorNode { outputSS[4bits], hasFarInput[1bit], farInputSS[4bits], mode[1bit] }\
**0100**: LampNode { lit[1bit], /*depowerTime[2bits]*/ }
**0101**: UserInputNode { ss[4bits] }

