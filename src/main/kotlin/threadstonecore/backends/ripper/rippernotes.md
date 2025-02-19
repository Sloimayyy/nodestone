
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

Bit layout: DDDDDDDD DDDDTTTT
T: Type bits
D: Data bits

### Edge layouts (same for inputs and outputs):

NNNNNNNN NNNNNNNN NNNNNNNN NNNSDDDD
N: Node idx (in graph array, not serialized arr)
S: Is side input
D: Redstone dist

### Graph array:
[
    Node0State0, Node1State0, .., 
    Node0State1, Node1State1, .. 
]

### Edge pointer array
[
    Node0InputEdgePointer, Node0OutputEdgePointer, Node0IoCount
    Node1InputEdgePointer, ..
]

### Edge array
[
    Node0Input0, Node0Input1, .. Node0Output0, Node0Output1, ..
]


### Nodes added this tick bitmap
[
    u32, ..
]

### ToUpdate array (will be expanded into channels)
[
    nodeIdx, nodeIdx, ..
]

### Last updated Timestamp array (UT = Update timestamp)
arr of u64:
[
    Node0State0UT, Node0State1UT, Node1State0UT, ..
]


## Optimisation ideas:
Make nodes be ints instead, and use the top 2 nibbles of the int be the node's constant
back and side inputs. Would make constant nodes non-existent for basically 0 cost.

Replace 1t reps (that aren't lockable) locking other reps by just the locked rep and a direct
side connection to it. Good work around for now
