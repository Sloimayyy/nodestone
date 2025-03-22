




Abstract redstone gate updatePowered function:

```
fun updatePowered {
    if (!computeIsLocked) {
        powerStateIsTrue
        hasPowerBack
        
        if (powerStateIsTrue != hasPowerBack && isNotAlreadyTicking) {
            
            tickPriority
            if (!abstractGateGoingIntoIsAligned) {
                tickPriority = EXTREMELY HIGH
            } eles if (powerStateIsTrue) {
                tickPriority = VERY HIGH
            } else {
                tickPriority = HIGH
            }
            
            scheduleTick( here, DELAY (*2 because it's gameticks), tickPriority )
            
        }
    }
}
```

Abstract redstione gate scheduledTick function:

```
fun onScheduledTick {
    if (!computeIsLocked) {
        powerStateIsTrue
        hasPowerBack
        
        if (powerStateIsTrue && doesn't have power back) {
            set this block as unpowered
        } else if (power state is false) {
            
            set this block as powered
            
            if (no power back) {
                scheduleTick( here, DELAY * 2 (cuz gt), VERY HIGH PRIORITY )
            }
        }
    }
}

```



World.setBlockState function
```

previousState = chunk.setBlockState(pos, stateTriedToPlace)
if (previousState == null) return false

statePlaced = chunk.getBlockState(pos)
if (statePlaced == stateTriedToPlace) {
    if (maxUpdateDepth > 0) {
        updateNeighbors
    }
}

```


set block state when repeater:
```

updateListeners:
    markForBlockUpdate here

for each neighbor in the 6 axes {
    neighbor = the rep the update comes from
    if this != neighborAxis {
        compute lock state
    } else {
        don't change state
    }
}
```
