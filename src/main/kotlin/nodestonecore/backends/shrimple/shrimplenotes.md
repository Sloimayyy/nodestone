






### Notes
The repeater update timer works by setting itself to the number "delay" when the repeater is off
(turns off or is updated as off)
Then decrements by 1 every tick until it hits 0 when the repeater is on. Every time it decrements
it asks the repeater to update itself, so that it can update its scheduler correctly.
(One ticking a 4t repeater used to make it keep itself on, but with this timer it continuously
updates itself for 4 ticks after the pulse until its scheduler realises correctly that it's
unpowered)
(You might be able to make the timer go up to 2 instead but wasn't tested)

Update priorities in the output edge and node layouts always match. The information is
just duplicated so we mem lookup less




### Node layout:

EEEEEEEE
DDDDDDDD
CCCCCCCC
XUUTTTTP

- **D**: Dynamic data on even ticks
- **E**: Dynamic data on odd ticks
- **C**: Constant data
- **P**: Parity bit
- **T**: Node type
- **U**: Update priority
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
  - Dynamic: schedulerBits[4bits], locked[1bit], updateTimer[3bits]
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


### Input edge layout:

NNNNNNNN NNNNNNNN NNNNNNNN NNNSDDDD
N: Node idx (in graph array, not serialized arr)
S: Is side input
D: Redstone dist

### Output edge layout:
NNNNNNNN NNNNNNNN NNNNNNNN NNUUDDDD
N: Node idx (in graph array, not serialized arr)
D: Redstone dist
U: Update priority


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



### Optimisation ideas

Store the bit that tells if a node has already been scheduled for an update directly
in the node itself as part of its dyn data? To benchmark with big builds.