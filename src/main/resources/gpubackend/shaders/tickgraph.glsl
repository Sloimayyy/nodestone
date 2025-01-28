#version 430
layout(local_size_x = "#WORK_GROUP_SIZE") in;

layout(std430, binding = 0) buffer GraphBuf {
    uint graphBuf[];
};
layout(std430, binding = 1) buffer NodeIndexes {
    uint nodeIndexes[];
};

uniform uint tickedGraphBase;
uniform uint receiverGraphBase;



// # Decomps don't decomp the nodeInputCount
// # Node data is nodeInt >> 4 !!!!! REMEMBER !!!!!!!!!!
uint decompTorch(in uint nodeData) {
    return (nodeData >> 0) & 1;
}

uvec3 decompRepeater(in uint nodeData) {
    return uvec3(
        (nodeData >> 0) & 0x1, // locked
        (nodeData >> 1) & 0x3, // delay
        (nodeData >> 3) & 0xF // scheduler
    );
}

uvec4 decompComparator(in uint nodeData) {
    return uvec4(
        (nodeData >> 0) & 0xF,
        (nodeData >> 4) & 0x1,
        (nodeData >> 5) & 0xF,
        (nodeData >> 9) & 0x1
    );
}

uint decompLamp(in uint nodeData) {
    return (nodeData >> 0) & 1;
}




uint recompTorch(in uint lit, in uint inputCount) {
    return "#torchId" | (lit << 4) | (inputCount << "#NODE_INPUT_COUNT_SHIFT");
}

uint recompRepeater(in uint locked, in uint delay, in uint scheduler, in uint inputCount) {
    return "#repId" | (locked << 4) | (delay << 5) | (scheduler << 7) | (inputCount << "#NODE_INPUT_COUNT_SHIFT");
}

uint recompComparator(in uint outputSs, in uint hasFarInput, in uint farInputSs, in uint mode, in uint inputCount) {
    return "#comparatorId" | (outputSs << 4) | (hasFarInput << 8) | (farInputSs << 9) | (mode << 13) | (inputCount << "#NODE_INPUT_COUNT_SHIFT");
}

uint recompLamp(in uint lit, in uint inputCount) {
    return "#redstoneLampId" | (lit << 4) | (inputCount << "#NODE_INPUT_COUNT_SHIFT");
}




uint getNodeDataBits(in uint nodeInt) {
    return (nodeInt >> 4) & 0xFFF;
}

uint getNodePower(in uint nodeInt) {
    uint nodeType = nodeInt & 0x0F;
    uint nodeData = getNodeDataBits(nodeInt);


    uint isTorch = uint(nodeType == "#torchId");
    uint isAnalog = uint((nodeType == "#comparatorId") || (nodeType == "#constantId") || (nodeType == "#userInputId"));
    uint isRep = uint(nodeType == "#repId");

    uint ssIfAnalog = nodeData & 0xF;
    uint ssIfTorch = (-(nodeData & 0x01)) & 15;

    uint repDelay = (nodeData >> 1) & 0x3;
    uint scheduler = (nodeData >> 3) & 0xF;
    uint ssIfRep = (-((scheduler >> repDelay) & 1)) & 15;

    uint ss = ((-isAnalog) & ssIfAnalog) + ((-isTorch) & ssIfTorch) + ((-isRep) & ssIfRep);

    return ss;



    switch (nodeType) {
        case "#repId": {
            uint repDelay = (nodeData >> 1) & 0x3;
            uint scheduler = (nodeData >> 3) & 0xF;
            uint isLit = (scheduler >> repDelay) & 1;
            return (-isLit) & 15; // Effectively "lit * 15" if lit is a boolean

            break;
        };

        case "#torchId": {
            uint lit = nodeData & 0x01;
            return (-lit) & 15; // Effectively "lit * 15"

            break;
        };

        /*case "#redstoneLampId": {
            uint lit = nodeData & 0x01;
            return (-lit) & 15; // Effectively "lit * 15"

            break;
        };*/

        case "#comparatorId": {
            uint ss = nodeData & 0xF;
            return ss;

            break;
        }

        case "#constantId": {
            uint ss = nodeData & 0xF;
            return ss;

            break;
        }

        case "#userInputId": {
            uint ss = nodeData & 0xF;
            return ss;

            break;
        }

        default:
            return 0;
    }
}

uvec2 getNodeInputPowers(uint nodeGlobalIndex, uint nodeInputCount) {
    // x is power behind, y is side power
    uvec2 outPowers = uvec2(0, 0);

    for (uint i = 0; i < nodeInputCount; i++) {
        // Get this input's global idx
        uint inputIdx = nodeGlobalIndex + 1 + i;
        uint inputData = graphBuf[inputIdx];
        uint inputNodeBaseIdx = inputData >> 5;
        uint inputNodeIdx = inputNodeBaseIdx + tickedGraphBase;
        // Get the power it's emitting
        uint power = getNodePower(graphBuf[inputNodeIdx]);
        // Deplete the power
        uint redstoneDist = inputData & 0x0F;
        uint depletedPower = uint(max(0, int(power) - int(redstoneDist)));
        // Max which side
        uint side = (inputData >> 4) & 0x01;
        outPowers[side] = max(outPowers[side], depletedPower);
        /*if (side == 0) {
            // Back
            outPowers.x = max(outPowers.x, depletedPower);
        } else {
            // Side
            outPowers.y = max(outPowers.y, depletedPower);
        }*/
    }

    return outPowers;
}






void main() {
    uint threadIndex = gl_GlobalInvocationID.x;

    if (threadIndex >= "#nodeCount") {
        return; // Ensure we don't go out of bounds
    }

    uint baseNodeIndex = nodeIndexes[threadIndex];

    // Index
    uint nodeGlobalIndex = baseNodeIndex + tickedGraphBase;
    uint nodeInt = graphBuf[nodeGlobalIndex];
    uint nodeType = nodeInt & 0x0F;
    uint nodeData = getNodeDataBits(nodeInt);
    uint nodeInputCount = nodeInt >> "#NODE_INPUT_COUNT_SHIFT";
    uint receiverIdx = baseNodeIndex + receiverGraphBase;

    switch (nodeType) {

        case "#constantId": {
            // Do nothing

            break;
        }

        case "#userInputId": {
            // Do nothing

            break;
        }

        case "#redstoneLampId": {

            uint lampDecomp = decompLamp(nodeData);
            uint lit = lampDecomp;

            uvec2 maxPowers = getNodeInputPowers(nodeGlobalIndex, nodeInputCount);
            uint maxPowerBack = maxPowers.x;
            lit = uint(maxPowerBack > 0);

            graphBuf[receiverIdx] = recompLamp(lit, nodeInputCount);

            break;
        }

        case "#repId": {

            uvec3 repDecomp = decompRepeater(nodeData);
            uint locked = repDecomp.x;
            uint delay = repDecomp.y; // encoded delay (real delay - 1, so 0 => 1 of real delay, 1 => 2 of real delay etc..)
            uint scheduler = repDecomp.z;

            uvec2 maxPowers = getNodeInputPowers(nodeGlobalIndex, nodeInputCount);
            uint maxPowerBack = maxPowers.x;
            uint maxPowerSide = maxPowers.y;

            locked = uint(maxPowerSide > 0);

            // Fearless' 4bit scheduler implementation
            uint repOutput = (scheduler >> delay) & 0x1;
            // Bit mask of [delay] length
            uint schedMask = (1 << (delay+1)) - 1;
            if (locked == 0) {
                uint input = uint(maxPowerBack > 0);
                if (input == 1 && repOutput == 1) {
                    scheduler = schedMask; // Set every bit of the sched to 1. Used for pulse extension
                } else {
                    // # LFSR magic
                    // Save first bit of the sched
                    uint schedFirstBit = scheduler & 0x1;
                    // Shift scheduler left
                    scheduler = (scheduler << 1) & 0xF;
                    // Fill the first bit with this magic
                    scheduler |= input | (~repOutput & schedFirstBit);
                }
            } else {
                // Set all the output bits of the scheduler to the output
                scheduler = schedMask * repOutput;
            }

            graphBuf[receiverIdx] = recompRepeater(locked, delay, scheduler, nodeInputCount);

            break;
        };


        case "#torchId": {

            /*uint maxPower = 0;
            for (uint i = 0; i < nodeInputCount; i++) {
                uint inputIdx = nodeGlobalIndex + 1 + i;
                uint inputData = graphBuf[inputIdx];
                uint redstoneDist = inputData & 0x1F;
                uint inputNodeBaseIdx = inputData >> 6;
                uint inputNodeIdx = inputNodeBaseIdx + tickedGraphBase;
                uint power = getNodePower(graphBuf[inputNodeIdx]);
                uint depletedPower = uint(max(0, int(power) - int(redstoneDist)));
                maxPower = max(maxPower, depletedPower);
            }*/
            uint torchDecomp = decompTorch(nodeData); // uvec1(lit)

            uint maxPower = getNodeInputPowers(nodeGlobalIndex, nodeInputCount).x;

            uint lit = torchDecomp;
            lit = uint(!(maxPower > 0));

            graphBuf[receiverIdx] = recompTorch(lit, nodeInputCount);

            break;
        };


        case "#comparatorId": {

            uvec4 compDecomp = decompComparator(nodeData);
            uint outputSs = compDecomp.x;
            uint hasFarInput = compDecomp.y;
            uint farInputSs = compDecomp.z;
            uint mode = compDecomp.w;

            uvec2 maxPowers = getNodeInputPowers(nodeGlobalIndex, nodeInputCount);
            uint maxPowerBack = maxPowers.x;
            uint maxPowerSide = maxPowers.y;

            if (hasFarInput == 1) {
                if (maxPowerBack < 15) {
                    maxPowerBack = farInputSs;
                }
            }

            if (mode == 0) {
                // Compare
                outputSs = maxPowerBack * uint(maxPowerSide <= maxPowerBack);
            } else {
                // Subtract
                outputSs = uint( max(int(maxPowerBack) - int(maxPowerSide), 0) );
            }

            graphBuf[receiverIdx] = recompComparator(outputSs, hasFarInput, farInputSs, mode, nodeInputCount);
            //graphBuf[receiverIdx] = (maxPowerBack << 16) | (maxPowerSide << 8) | (outputSs << 24) | (mode << 28);

            break;
        }





        /*case "#torchId": {
            uint lit = nodeData & 0x01;

            uint maxPower = 0;

            uint p1 = 0;
            uint p2 = 0;

            uint a = 0;

            for (uint i = 0; i < nodeInputCount; i++) {
                uint inputIdx = nodeIndex + 1 + i;
                uint inputData = graphBuf[inputIdx];
                uint redstoneDist = inputData & 0x1F;
                // Don't get side as torches don't have side inputs
                uint inputNodeBaseIdx = inputData >> 6;
                uint inputNodeIdx = inputNodeBaseIdx + tickedGraphBase;
                uint power = getNodePower(inputNodeIdx);
                uint depletedPower = uint( max(0, int(power) - int(redstoneDist)) );
                p1 = depletedPower;
                if (depletedPower > maxPower) {
                    maxPower = depletedPower * 2;
                    p1 = depletedPower * 2;
                }
                a += 1;
                //maxPower = max(depletedPower, maxPower);
            }

            if (maxPower > 0) {
                lit = 1 - lit;
            }

            graphBuf[baseNodeIndex + receiverGraphBase] = p1;//recompTorch(lit, nodeInputCount);
        }; break;*/
    }

}