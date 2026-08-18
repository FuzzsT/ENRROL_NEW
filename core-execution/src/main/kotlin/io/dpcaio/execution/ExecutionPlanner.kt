package io.dpcaio.execution

import io.dpcaio.core.model.BuildTrack
import io.dpcaio.core.model.CapabilityRequest
import io.dpcaio.core.model.ExecutionPlan
import io.dpcaio.core.model.ExecutionRoute

class ExecutionPlanner {
    fun plan(request: CapabilityRequest, routes: List<ExecutionRoute>): ExecutionPlan {
        val allowLab = request.buildTrack == BuildTrack.LAB_DEBUG
        val candidates = routes
            .asSequence()
            .filter { it.available }
            .filter { allowLab || (it.releaseEligible && !it.labOnly) }
            .sortedByDescending { it.score }
            .toList()
        return ExecutionPlan(request, candidates)
    }
}
