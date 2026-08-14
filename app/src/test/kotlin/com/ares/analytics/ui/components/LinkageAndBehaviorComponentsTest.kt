package com.ares.analytics.ui.components

import com.areslib.behavior.BehaviorNodeDocument
import com.areslib.behavior.BehaviorNodeKind
import com.areslib.behavior.BehaviorTreeDocument
import com.areslib.behavior.BehaviorTreeEvaluator
import com.areslib.behavior.BehaviorStatus
import com.areslib.math.kinematics.TwoDofLinkageKinematics
import com.areslib.math.kinematics.TwoDofLinkageParameters
import com.areslib.subsystem.SubsystemLinkageDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkageAndBehaviorComponentsTest {

    @Test
    fun `linkage parameters model initializes and calculates physical workspace envelope`() {
        val linkageDoc = SubsystemLinkageDocument(
            enabled = true,
            link1LengthMeters = 0.35,
            link2LengthMeters = 0.25,
            link1MassKg = 0.6,
            link2MassKg = 0.4,
        )
        val params = TwoDofLinkageParameters(
            l1 = linkageDoc.link1LengthMeters,
            l2 = linkageDoc.link2LengthMeters,
            m1 = linkageDoc.link1MassKg,
            m2 = linkageDoc.link2MassKg,
        )
        val kinematics = TwoDofLinkageKinematics(params)

        assertEquals(0.60, params.maxReach, 1e-4)
        assertEquals(0.10, params.minReach, 1e-4)
        assertTrue(kinematics.isReachable(0.40, 0.20))
    }

    @Test
    fun `behavior tree document constructs valid runtime evaluator with state matching`() {
        val root = BehaviorNodeDocument(
            nodeId = "root",
            kind = BehaviorNodeKind.SEQUENCE,
            children = listOf(
                BehaviorNodeDocument(
                    nodeId = "cond_has_sample",
                    kind = BehaviorNodeKind.CONDITION,
                    targetField = "intake.hasSample",
                    expectedBooleanValue = true,
                ),
                BehaviorNodeDocument(
                    nodeId = "act_index",
                    kind = BehaviorNodeKind.ACTION,
                    actionKey = "indexer.intake_and_hold",
                ),
            ),
        )
        val tree = BehaviorTreeDocument(
            treeId = "index_routine",
            displayName = "Auto Index Routine",
            rootNode = root,
        )
        val evaluator = BehaviorTreeEvaluator(tree)
        val fired = mutableListOf<String>()

        val status = evaluator.tick(
            stateLookup = { field -> if (field == "intake.hasSample") true else null },
            actionDispatcher = { key, _ -> fired.add(key) },
        )

        assertEquals(BehaviorStatus.SUCCESS, status)
        assertEquals(listOf("indexer.intake_and_hold"), fired)
    }
}
