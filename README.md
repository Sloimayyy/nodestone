Standalone redstone simulator with multiple backends.

Currently 4 exists: `gpubackend`, `mamba`, `ripper` and `shrimple`\
The first 3 don't compile anymore and were left in various unfinished states. But I keep them there as archives.
Just a thing I do a lot lol.

Depends on: mcvolume, smath and gson.
Could look into shadowing in some way

### Contributing
okok this section won't be professional at all but rn the main focus is trying to figure out how a unified interface for
*almost any backend* would look like. As backends can be very diverse, from computational redstone, to vanilla, we need a way
to represent Minecraft -> Nodestone and Nodestone -> Minecraft interactions in a universal way.
