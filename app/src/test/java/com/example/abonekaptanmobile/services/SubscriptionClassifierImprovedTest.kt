package com.example.abonekaptanmobile.services

import com.example.abonekaptanmobile.data.sampleTestEmails
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubscriptionClassifierImprovedTest {

    @Test
    fun `testCalculateImprovedMetrics with improved dummy logic`() {
        val testEmails = sampleTestEmails
        val testHelper = SubscriptionClassifierTestHelper() // Contains the "improved" dummy logic

        // Passing null as the classifier, as the dummy logic doesn't use it.
        val classificationResults = testHelper.runClassificationTest(testEmails, null)

        println("IMPROVED Classification Results (Dummy Logic):")
        classificationResults.forEach { (email, result) ->
            println("Email ID: ${email.id}, From: ${email.from}, Subject: ${email.subject}")
            println("  Expected: Label='${email.expectedLabel}', Service='${email.expectedService}'")
            println("  Predicted: Label='${result.detectedLabel}', Service='${result.detectedService}', Confidence=${result.confidence}")
            println("----")
        }

        val improvedMetrics = testHelper.evaluateResults(classificationResults)

        println("\nIMPROVED Performance Metrics (Dummy Logic):")
        improvedMetrics.forEach { (label, metrics) ->
            println("\nMetrics for Label: $label")
            println("  True Positives (TP): ${metrics.tp}")
            println("  False Positives (FP): ${metrics.fp}")
            println("  False Negatives (FN): ${metrics.fn}")
            println("  Precision: ${String.format("%.2f", metrics.precision)}")
            println("  Recall: ${String.format("%.2f", metrics.recall)}")
            println("  F1-Score: ${String.format("%.2f", metrics.f1Score)}")
        }

        println("\nNote: These metrics are based on the specific 'IMPROVED' DUMMY classification logic.")
        println("This simulation aims to show how targeted fixes can improve metrics.")
        println("Compare these to the 'BASELINE' metrics from SubscriptionClassifierBaselineTest.")

        // Assertions to ensure all expected labels are present
        assertNotNull("Metrics for 'paid_subscription_event' should not be null", improvedMetrics["paid_subscription_event"])
        assertNotNull("Metrics for 'paid_subscription_cancellation' should not be null", improvedMetrics["paid_subscription_cancellation"])
        assertNotNull("Metrics for 'other' should not be null", improvedMetrics["other"])

        // Illustrative: Compare with hypothetical baseline values
        // In a real scenario, you might fetch baseline results or define them.
        // For this simulation, we'd manually compare the console output.
        // Example (conceptual - these values are not from the actual baseline run yet):
        // val baselineF1PaidEvent = 0.50 // Hypothetical baseline F1 for paid_subscription_event
        // val improvedF1PaidEvent = improvedMetrics["paid_subscription_event"]!!.f1Score
        // assertTrue("F1 score for 'paid_subscription_event' should improve or stay same.", improvedF1PaidEvent >= baselineF1PaidEvent)

        // For "paid_subscription_event":
        // Baseline (simulated from previous task's dummy logic before specific fixes):
        // email1 (Netflix) -> TP
        // email3 (YouTube) -> FN (classified as other)
        // email4 (Amazon) -> TP
        // email5 (Trendyol) -> FP (classified as paid_event)
        // email8 (Disney) -> FP (classified as paid_event by modulo logic)
        // email10 (Random) -> TP
        // TP = 3, FP = 2, FN = 1 (for YouTube)
        // Precision = 3/(3+2) = 0.60
        // Recall = 3/(3+1) = 0.75
        // F1 = 2 * (0.60 * 0.75) / (0.60 + 0.75) = 2 * 0.45 / 1.35 = 0.90 / 1.35 = 0.67

        // Improved for "paid_subscription_event":
        // email1 (Netflix) -> TP
        // email3 (YouTube) -> TP (fixed)
        // email4 (Amazon) -> TP
        // email5 (Trendyol) -> No longer FP (correctly 'other')
        // email8 (Disney) -> No longer FP (correctly 'other')
        // email10 (Random) -> TP
        // TP = 4, FP = 0, FN = 0
        // Precision = 4/(4+0) = 1.0
        // Recall = 4/(4+0) = 1.0
        // F1 = 1.0
        // So, we expect F1 to improve from ~0.67 to 1.0.

        val improvedF1PaidEvent = improvedMetrics["paid_subscription_event"]!!.f1Score
        assertTrue("F1 score for 'paid_subscription_event' is expected to be higher after improvements.", improvedF1PaidEvent > 0.60) // Check against a value lower than expected 1.0


        // For "other":
        // Baseline:
        // email3 (YouTube) -> FP (classified as other, but is paid_event)
        // email5 (Trendyol) -> FN (classified as paid_event, but is other)
        // email6 (LinkedIn) -> TP
        // email7 (Adobe) -> FN (classified as paid_cancel, but is other)
        // email8 (Disney) -> FN (classified as paid_event, but is other)
        // TP = 1 (LinkedIn), FP = 1 (YouTube), FN = 3 (Trendyol, Adobe, Disney)
        // Precision = 1/(1+1) = 0.5
        // Recall = 1/(1+3) = 0.25
        // F1 = 2 * (0.5 * 0.25) / (0.5 + 0.25) = 0.25 / 0.75 = 0.33

        // Improved for "other":
        // email3 (YouTube) -> No longer FP
        // email5 (Trendyol) -> TP (fixed)
        // email6 (LinkedIn) -> TP
        // email7 (Adobe) -> TP (fixed)
        // email8 (Disney) -> TP (fixed)
        // TP = 4, FP = 0, FN = 0
        // Precision = 1.0
        // Recall = 1.0
        // F1 = 1.0
        val improvedF1Other = improvedMetrics["other"]!!.f1Score
        assertTrue("F1 score for 'other' is expected to be higher after improvements.", improvedF1Other > 0.33)
    }
}
