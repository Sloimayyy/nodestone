#version 430

layout(local_size_x = "#WORK_GROUP_SIZE") in;


layout(std430, binding = 0) buffer GraphBuf {
    uint graphBuf[];
};
layout(std430, binding = 1) buffer InputBuf {
    uint inputBuf[];
};

uniform uint iterCount;











uint decompTorchData(in uint nodeData) {
    return (nodeData >> 0) & 1;
}
uint recompTorchData(in uint lit) {
    return (lit & 0x1);
}

uint decompLampData(in uint nodeData) {
    return (nodeData >> 0) & 1;
}
uint recompLampData(in uint lit) {
    return (lit & 0x1);
}

uvec3 decompRepData(in uint nodeData) {
    return uvec3(
        (nodeData >> 0) & 0xF, // Scheduler
        (nodeData >> 4) & 0x1, // Locked
        (nodeData >> 5) & 0x3 // Delay
    );
}
uint recompRepData(in uint scheduler, in uint locked, in uint delay) {
    return (scheduler & 0xF) | ((locked & 0x1) << 4) | ((delay & 0x3) << 5);
}

uvec4 decompComparatorData(in uint nodeData) {
    return uvec4(
        (nodeData >> 0) & 0xF, // outputSs
        (nodeData >> 4) & 0x1, // has far input
        (nodeData >> 5) & 0xF, // far input ss
        (nodeData >> 9) & 0x1  // mode
    );
}
uint recompComparatorData(in uint outputSs, in uint hasFarInput, in uint farInputSs, in uint mode) {
    return (outputSs << 0) | (hasFarInput << 4) | (farInputSs << 5) | (mode << 9);
}




uint getParityShift(in uint parityOffset) {
    uint parity = (iterCount + parityOffset) & 0x1;
    uint base = "#NODE_DATA_DO_UPDATE_BIT_COUNT" + "#NODE_TYPE_BIT_COUNT";
    uint offset = parity * ("#NODE_DATA_BIT_COUNT" + "#NODE_DATA_DO_UPDATE_BIT_COUNT");
    return base + offset;
}


uint getNodeDataBits(in uint nodeInt) {
    return (nodeInt >> getParityShift(0)) & "#NODE_DATA_BITS_BASE_MASK";
}

/**
Sets the data bits of the next state
*/
uint getNodeNext(in uint nodeInt, in uint dataBits) {
    uint shift = getParityShift(1);
    uint cleanedNodeInt = (nodeInt & (~("#NODE_DATA_BITS_BASE_MASK" << shift)));
    uint dataToWrite = ((dataBits & "#NODE_DATA_BITS_BASE_MASK") << shift);
    return cleanedNodeInt | dataToWrite;
}





uint getNodePower(in uint nodeInt) {
    uint nodeType = nodeInt & 0xF;
    uint dataBits = getNodeDataBits(nodeInt);

    switch (nodeType) {
        case "#torchNodeId": {
            uint lit = dataBits & 0x1;
            return (-lit) & 15; // Effectively "lit * 15"

            break;
        };

        case "#repeaterNodeId": {
            uint scheduler = dataBits & 0xF;
            uint isPowered = scheduler & 0x1;
            return (-isPowered) & 15; // Effectively "isPowered * 15" if isPowered is a boolean

            break;
        };


        case "#comparatorNodeId": {
            uint ss = dataBits & 0xF;
            return ss;

            break;
        }

        case "#constantNodeId": {
            uint ss = dataBits & 0xF;
            return ss;

            break;
        }

        case "#userInputNodeId": {
            uint ss = dataBits & 0xF;
            return ss;

            break;
        }

        default:
            return 0;
    }
}



uvec2 getNodeInputPowers(in uint inputPointer) {
    // x is back power, y is side power
    uvec2 outPowers = uvec2(0, 0);

    bool hasInputs = bool(inputPointer & 1);
    if (!hasInputs) { return outPowers; }

    uint ptrIdx = inputPointer >> 1;
    uint currPointerData;
    bool isLastInput;
    do {
        currPointerData = inputBuf[ptrIdx];
        uint redstoneDist = (currPointerData >> 0) & 0xF;
        uint side = (currPointerData >> 4) & 0x1;
        isLastInput = bool((currPointerData >> 5) & 0x1);
        uint nodeIdx = currPointerData >> 6;
        uint nodeGraphIdx = nodeIdx * "#NODE_LEN_IN_ARRAY";

        // TODO: Has to be atomic reads??? EWWW
        uint pointedNodeInt = atomicOr(graphBuf[nodeGraphIdx], 0);
        uint power = getNodePower(pointedNodeInt);

        uint depletedPower = uint(max(0, int(power) - int(redstoneDist)));
        outPowers[side] = max(outPowers[side], depletedPower);

        ptrIdx += 1;
    } while (!isLastInput);

    return outPowers;
}







void main() {

    uint threadIdx = gl_GlobalInvocationID.x;
    if (threadIdx >= "#NODE_COUNT") {
        return;
    }

    uint nodeGraphIdx = threadIdx * "#NODE_LEN_IN_ARRAY";
    uint nodeInt = atomicOr(graphBuf[nodeGraphIdx], 0); // Atomic read
    uint nodeInputPointer = graphBuf[nodeGraphIdx + 1];
    uint nodeType = nodeInt & 0xF;
    uint dataBits = getNodeDataBits(nodeInt);


    switch (nodeType) {
        case "#constantNodeId": { break; }
        case "#userInputNodeId": { break; }

        case "#torchNodeId": {
            uint torchDecomp = decompTorchData(dataBits); // uvec1(lit)
            uint maxPower = getNodeInputPowers(nodeInputPointer).x;

            uint lit = torchDecomp;
            lit = uint(!(maxPower > 0));

            uint newDataBits = recompTorchData(lit);
            uint newNodeInt = getNodeNext(nodeInt, newDataBits);
            atomicExchange(graphBuf[nodeGraphIdx], newNodeInt);
            break;
        }

        case "#repeaterNodeId": {
            uvec3 repDecomp = decompRepData(dataBits);
            uint scheduler = repDecomp.x;
            uint locked = repDecomp.y;
            uint delay = repDecomp.z;

            uvec2 maxPowers = getNodeInputPowers(nodeInputPointer);
            uint maxPowerBack = maxPowers.x;
            uint maxPowerSide = maxPowers.y;

            locked = uint(maxPowerSide > 0);

            // Mamba's implementation of the scheduler is inverted, the output bit is the first one of the
            // scheduler, not the last one.
            uint repOutput = scheduler & 0x1;
            // bit mask of [delay] length
            uint schedMask = (1 << (delay+1)) - 1;
            if (locked == 0) {
                uint input = uint(maxPowerBack > 0);
                if (input == 1 && repOutput == 1) {
                    scheduler = schedMask; // Pulse extension
                } else {
                    // # LFSR magic
                    // First bit is at the end of the scheduler
                    uint schedFirstBit = (scheduler >> delay) & 0x1;
                    // Shift scheduler
                    scheduler = ((scheduler & schedMask) >> 1); // TODO: replace 0xF by sched mask
                    // Fill the first bit with this magic
                    scheduler |= (input | ((~repOutput) & schedFirstBit)) << delay;
                }
            } else {
                // Set all output bits of the scheduler to the output
                scheduler = schedMask * repOutput;
            }

            uint newDataBits = recompRepData(scheduler, locked, delay);
            uint newNodeInt = getNodeNext(nodeInt, newDataBits);
            atomicExchange(graphBuf[nodeGraphIdx], newNodeInt);
            break;
        }

        case "#comparatorNodeId": {

            uvec4 compDecomp = decompComparatorData(dataBits);
            uint outputSs = compDecomp.x;
            uint hasFarInput = compDecomp.y;
            uint farInputSs = compDecomp.z;
            uint mode = compDecomp.w;

            uvec2 maxPowers = getNodeInputPowers(nodeInputPointer);
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

            uint newDataBits = recompComparatorData(outputSs, hasFarInput, farInputSs, mode);
            uint newNodeInt = getNodeNext(nodeInt, newDataBits);
            atomicExchange(graphBuf[nodeGraphIdx], newNodeInt);

            break;
        }

        case "#lampNodeId": {
            uint lampDecom = decompLampData(dataBits); // uvec1(lit)
            uint maxPower = getNodeInputPowers(nodeInputPointer).x;

            uint lit = lampDecom;
            lit = uint(maxPower > 0);

            uint newDataBits = recompLampData(lit);
            uint newNodeInt = getNodeNext(nodeInt, newDataBits);
            atomicExchange(graphBuf[nodeGraphIdx], newNodeInt);
            break;
        }

    }

}