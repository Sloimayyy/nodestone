




Identity nodes in chunks, so that nodes close together are more likely to be close together in mem too. 
Basically the goal is to make the maximum amount of components next to each other in a warp?
There's many sub-chunking ways to do so too. Like build a chunk until there's 32 in a chunk. If there's too
many, subdivide the chunk and try again? Stuff like that
Also we could also try sub-chunking but after nodes were identified? Like once we have all the
positioned nodes, then we can move them around in the array
**^ To try very soon, this sounds like a super easy great improvement**

When in IO-only, only do the node change checks on the IO nodes, don't go over every node.
Not that bad for <500k node builds can get pretty bad quickly after

Comparator -> torch
Remove 0 constants
Remove inputs that are too far away (mostly already done)
Comparator max SS they can output optimisations
