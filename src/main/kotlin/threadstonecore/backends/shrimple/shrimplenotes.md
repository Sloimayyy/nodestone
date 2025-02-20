











### Node layout:

EEEEEEEE
DDDDDDDD
CCCCCCCC
XXXTTTTP

- **D**: Dynamic data on even ticks
- **E**: Dynamic data on odd ticks
- **C**: Constant data
- **T**: Node type
- **X**: Unused

#### Per type node layouts:

- Type 0000: Constant
  - Dynamic: ss[4bits]

- Type 0001: UserInput
  - Dynamic: ss[4bits]
  
- Type 0010: Comparator
  - Const: hasFarInput[1bit], farInputSs[4bits], mode[1bit]
  - Dynamic: outputSs[4bits]
  
- Type 0011: Lamp
  - Dynamic: lit[1bit]

- Type 0100: Torch
  - Dynamic: lit[1bit]

- Type 0101: Repeater
  - Dynamic: schedulerBits[4bits], locked[1bit]
  - Const: delay[2bits]



### Arrays
#### Graph array
[
    Node0, Node1, ..
]
#### Edge pointer array
[
    Node0InputEdgePointer, Node0OutputEdgePointer, Node0IoCount
    Node1InputEdgePointer, ..
]
### Edge array
[
    Node0Input0, Node0Input1, .. Node0Output0, Node0Output1, ..
]

### ToUpdate array
[
    nodeIdx, nodeIdx, ..
]
#### Nodes added this tick bitmap:
[
    u64..
]


### Edge layouts (same for inputs and outputs):

NNNNNNNN NNNNNNNN NNNNNNNN NNNSDDDD
N: Node idx (in graph array, not serialized arr)
S: Is side input
D: Redstone dist


### Timestamp array
Timestamp: TTTT..TTTT
T: Timestamp

When looking at a node:
if timestamp == currentTick:
    // it means this node was updated at this tick, but we want its state last tick
    // so we should get the data of the node where the parity isn't pointing
else:
    // timestamp is less than currentTick (because currentTick only goes forward)
    // so that means that the state of node is where the parity is pointing

