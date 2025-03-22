


Have a function in the shader that takes in an index,
and updates the component at that index.



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



### TODO IDEAS:
- Store the node edges only once, like if you duplicate the graph, don't
duplicate the input lists
- User input nodes can just be normal constant nodes, but I'd rather not yet, as
they may interfere with constant node optimisations.



### OLD:
**1110**: RedstoneWireNode {  }\
**1111**: RedstoneOutputNode { outputSS[4bits], distanceMapIntCount[4bits] }

The redstone wire problem:\
Basically, every component is going to have its single
update thread. A redstone wire is instant, and is the max()
of its inputs. Most components actually take in the max() of
their inputs, like an or gate. So for all components
you're just going to update its output depending on input.
Easy enough. But a redstone wire is instant, so we can't
just "do that", otherwise the redstone wire is gonna have
a delay of one tick.\
So to fix that, we're going to regroup every redstone wire
node and its inputs into one thread. Like, in a single
thread, we update every input, and then the redstone wire.
Every other node is gonna have its own dedicated thread,
but those at happen to output into a redstone wire will
be processed in the same thread as the redstone wire.\
Also, it could be smart to dispatch every redstone wire
groups into the same workgroup(s), as they're going to be
the processing bottleneck of nodes.

Redstone wires are different:\
[
RedstoneWireNode,
[Inputs..],
[
RedstoneOutputNode,
InputDistanceMap.. (basically a ubyte array)
]..
]\
A redstone wire nodes and redstone output nodes are
different nodes. No thread is dispatched for a redstone
output node as it is managed by the redstone wire node on
update. But, they'll be used by other nodes as inputs,
that's where they'll get their input SS.