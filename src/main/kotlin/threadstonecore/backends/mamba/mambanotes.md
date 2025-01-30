




### Node layout:

Type: 4bits\

types and their data bits:
- **Hex outputters:**
- 0000: Constant
  - ss: 4bits
- 0001: UserInput
  - ss: 4bits
- 0010: Comparator
  - outputSs: 4bits
  - hasFarInput: 1bit
  - farInputSs: 4bits
  - mode: 1bit
- **Bin outputters with output bit being the first one:**
- 0011: Lamp
  - lit: 1bit
- 0100: Torch
  - lit: 1bit
- 0101: Repeater
  - schedulerBits: 4bits
  - locked: 1bit,
  - delay: 2bits,

Layout: <Type> <DoUpdate> <DataBits>

#### Input arr ptr layout
HasInputs: 1bit\
Idx: 31bits

#### Input bit layout
Redstone dist: 4bits\
Is side input: 1bit\
Is last input: 1bit\
Last 26 bits: Node idx (not in array)



#### Old Arrays:
Node array: [Node, InputPointer, Node, InputPointer, ..]\
Input array: [Node0Input0, Node0Input1, .., Node0InputLast, Node1Input0, ..]

#### New arrays
Node array: [NodeEven, NodeOdd, InputPointer, ...]
InputArray: Same

#### Later / ideas:
The goal is to first see if having the inputs in a separate array is better,
as well as testing having both states of the node in the same uint.
And then to have inputs and outputs in this array and try the update bit
implementation out.\
Verdict: Terrible perf for optimised graphs, the ones that reuse
nodes often, like constants.\
Having the nodes be contiguous and the inputs at the end of the graph buffer could be a good
place for cache too. Like make the graph buffer have a node half, and an input half.

Repeaters with the update bit method, are really finnicky.
So my first idea was to make everything work with the update bit
except repeaters with 2 or more of delay. Unfortunately, reps
with 2 or more delay ticking constantly, may frick up SO many
warps.
So, if we want to actually solve the problem, what if we only
ticked repeaters if their scheduler is in an in-between state
between fully filled with 1's and fully filled with 0's. That
sounds like it makes sense