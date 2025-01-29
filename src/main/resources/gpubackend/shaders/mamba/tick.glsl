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