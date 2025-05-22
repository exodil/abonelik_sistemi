package com.example.abonekaptanmobile.services

import com.example.abonekaptanmobile.data.local.dao.UserSubscriptionDao
import com.example.abonekaptanmobile.data.local.entity.UserSubscriptionEntity
import com.example.abonekaptanmobile.data.repository.CommunityPatternRepository
import com.example.abonekaptanmobile.data.repository.HuggingFaceRepository
import com.example.abonekaptanmobile.model.RawEmail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest // Preferred for modern coroutines testing
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SubscriptionClassifierTest {

    @Mock
    private lateinit var mockHuggingFaceRepository: HuggingFaceRepository

    @Mock
    private lateinit var mockCommunityPatternRepository: CommunityPatternRepository

    @Mock
    private lateinit var mockUserSubscriptionDao: UserSubscriptionDao

    @Captor
    private lateinit var userSubscriptionEntityCaptor: ArgumentCaptor<UserSubscriptionEntity>

    private lateinit var classifier: SubscriptionClassifier

    private val testEmail = RawEmail("testId1", "Test Subject", "test@example.com", System.currentTimeMillis(), "Test Snippet", "Test Body")
    private val testServiceName = "TestService"
    private val testUserId: String? = null // Assuming null for these tests as per current classifier logic

    @Before
    fun setUp() {
        classifier = SubscriptionClassifier(
            mockCommunityPatternRepository,
            mockHuggingFaceRepository,
            mockUserSubscriptionDao
        )
        // Common mocks can be set up here if needed, e.g., for determineServiceName if it's not the focus
        // For now, assuming determineServiceName works and focus on DB interactions
    }

    // --- Tests for handlePaidSubscriptionEvent ---

    @Test
    fun `testHandlePaidSubscriptionEvent_newSubscription_createsNewActiveEntry`() = runTest {
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(null)

        // Directly call the private method using reflection or make it internal for testing
        // For this exercise, I'll assume it's made testable (e.g. internal or package-private)
        // If not, would need to test via classifyEmails with specific lifecycleResult mocking
        // For now, I'll write it as if I can call it. Let's assume a helper or it's internal.
        // This is a common challenge with private methods. The ideal is to test public API.
        // Given the subtask, I will simulate direct test of the logic.
        
        // Simulate the conditions under which handlePaidSubscriptionEvent would be called
        // For this test, we are focusing on the DAO interaction part of handlePaidSubscriptionEvent
        // So we invoke it directly.
        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionEvent", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }

        verify(mockUserSubscriptionDao).insert(userSubscriptionEntityCaptor.capture())
        val capturedEntity = userSubscriptionEntityCaptor.value
        assertEquals(STATUS_ACTIVE, capturedEntity.status)
        assertEquals(testEmail.date, capturedEntity.subscriptionStartDate)
        assertEquals(testEmail.id, capturedEntity.lastEmailIdProcessed)
        assertEquals(testServiceName, capturedEntity.serviceName)
    }

    @Test
    fun `testHandlePaidSubscriptionEvent_existingActiveSubscription_updatesConfirmationDate`() = runTest {
        val existingActiveSub = UserSubscriptionEntity(
            id = 1L, serviceName = testServiceName, userId = testUserId, status = STATUS_ACTIVE,
            subscriptionStartDate = testEmail.date - 10000, lastEmailIdProcessed = "oldEmailId",
            lastActiveConfirmationDate = testEmail.date - 5000, createdAt = System.currentTimeMillis() - 20000
        )
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(existingActiveSub)

        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionEvent", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }

        verify(mockUserSubscriptionDao).update(userSubscriptionEntityCaptor.capture())
        val capturedEntity = userSubscriptionEntityCaptor.value
        assertEquals(STATUS_ACTIVE, capturedEntity.status)
        assertEquals(testEmail.date, capturedEntity.lastActiveConfirmationDate)
        assertEquals(testEmail.id, capturedEntity.lastEmailIdProcessed)
        assertEquals(existingActiveSub.id, capturedEntity.id) // Ensure it's an update
    }

    @Test
    fun `testHandlePaidSubscriptionEvent_resubscriptionAfterCancellation_createsNewActiveEntry`() = runTest {
        val cancelledSubEndDate = testEmail.date - 1000 // Ensure email is after this
        val existingCancelledSub = UserSubscriptionEntity(
            id = 1L, serviceName = testServiceName, userId = testUserId, status = STATUS_CANCELLED,
            subscriptionStartDate = testEmail.date - 20000, subscriptionEndDate = cancelledSubEndDate,
            lastEmailIdProcessed = "oldEmailId", lastActiveConfirmationDate = testEmail.date - 10000,
            createdAt = System.currentTimeMillis() - 30000
        )
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(existingCancelledSub)

         classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionEvent", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }

        verify(mockUserSubscriptionDao).insert(userSubscriptionEntityCaptor.capture())
        val capturedEntity = userSubscriptionEntityCaptor.value
        assertEquals(STATUS_ACTIVE, capturedEntity.status)
        assertEquals(testEmail.date, capturedEntity.subscriptionStartDate)
        assertNull(capturedEntity.subscriptionEndDate) // New active sub shouldn't have end date
    }

    @Test
    fun `testHandlePaidSubscriptionEvent_paidEventForOldCancelledSubscription_noAction`() = runTest {
        val cancelledSubEndDate = testEmail.date + 1000 // Email date is BEFORE or same as end date
         val existingCancelledSub = UserSubscriptionEntity(
            id = 1L, serviceName = testServiceName, userId = testUserId, status = STATUS_CANCELLED,
            subscriptionStartDate = testEmail.date - 20000, subscriptionEndDate = cancelledSubEndDate,
            lastEmailIdProcessed = "oldEmailId", lastActiveConfirmationDate = testEmail.date - 10000,
            createdAt = System.currentTimeMillis() - 30000
        )
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(existingCancelledSub)

        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionEvent", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }
        // Verify that specific update/insert for new/active subscription is not called.
        // It might log, which is fine. We care that it doesn't change DB state for this condition.
        verify(mockUserSubscriptionDao, never()).insert(any())
        // verify(mockUserSubscriptionDao, never()).update(argThat { it.status == STATUS_ACTIVE }) // More specific if needed
        // Current logic might call update to change lastEmailIdProcessed if it's not a new sub.
        // The instruction is "neither insert nor update is called ... related to creating a new subscription or updating the specific fields handlePaidSubscriptionEvent targets"
        // The log message for this case is: "Paid event for ... but current status is CANCELLED and email date ... is not after endDate ... No action taken."
        // This implies no insert, and no update that would make it active or change its core dates.
        // If the only update could be for lastEmailIdProcessed on the CANCELLED record, that's out of scope for this test's primary assertion.
        // For simplicity, we check no insert and no update that changes status to active or modifies active-related fields.
        // The current implementation logs and does no DAO ops for this path.
        verify(mockUserSubscriptionDao, never()).update(any())
    }

    // --- Tests for handlePaidSubscriptionCancellation ---

    @Test
    fun `testHandlePaidSubscriptionCancellation_activeSubscription_updatesToCancelled`() = runTest {
        val existingActiveSub = UserSubscriptionEntity(
            id = 1L, serviceName = testServiceName, userId = testUserId, status = STATUS_ACTIVE,
            subscriptionStartDate = testEmail.date - 10000, // Start date before current email
            lastEmailIdProcessed = "oldEmailId", lastActiveConfirmationDate = testEmail.date - 5000,
            createdAt = System.currentTimeMillis() - 20000
        )
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(existingActiveSub)

        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionCancellation", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }

        verify(mockUserSubscriptionDao).update(userSubscriptionEntityCaptor.capture())
        val capturedEntity = userSubscriptionEntityCaptor.value
        assertEquals(STATUS_CANCELLED, capturedEntity.status)
        assertEquals(testEmail.date, capturedEntity.subscriptionEndDate)
        assertEquals(testEmail.id, capturedEntity.lastEmailIdProcessed)
    }

    @Test
    fun `testHandlePaidSubscriptionCancellation_noActiveSubscription_noAction_ifReturnsNull`() = runTest {
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(null)

        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionCancellation", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }
        verify(mockUserSubscriptionDao, never()).update(any())
    }
    
    @Test
    fun `testHandlePaidSubscriptionCancellation_noActiveSubscription_noAction_ifReturnsCancelled`() = runTest {
        val existingCancelledSub = UserSubscriptionEntity(
            id = 1L, serviceName = testServiceName, userId = testUserId, status = STATUS_CANCELLED,
            subscriptionStartDate = testEmail.date - 10000, subscriptionEndDate = testEmail.date - 500,
            lastEmailIdProcessed = "oldEmailId", lastActiveConfirmationDate = testEmail.date - 10000,
            createdAt = System.currentTimeMillis() - 30000
        )
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(existingCancelledSub)

        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionCancellation", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, testEmail, testServiceName, testUserId)
        }
        verify(mockUserSubscriptionDao, never()).update(any())
    }


    @Test
    fun `testHandlePaidSubscriptionCancellation_cancellationEmailBeforeStartDate_noAction`() = runTest {
        val emailDateBeforeStart = System.currentTimeMillis() - 20000
        val subStartDate = System.currentTimeMillis() - 10000 // Start date after email

        val emailForTest = RawEmail("testId2", "Cancel Subject", "cancel@example.com", emailDateBeforeStart, "Snippet", "Body")

        val existingActiveSub = UserSubscriptionEntity(
            id = 1L, serviceName = testServiceName, userId = testUserId, status = STATUS_ACTIVE,
            subscriptionStartDate = subStartDate,
            lastEmailIdProcessed = "oldEmailId", lastActiveConfirmationDate = subStartDate,
            createdAt = System.currentTimeMillis() - 30000
        )
        `when`(mockUserSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(testServiceName, testUserId)).thenReturn(existingActiveSub)

        classifier.javaClass.getDeclaredMethod("handlePaidSubscriptionCancellation", RawEmail::class.java, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(classifier, emailForTest, testServiceName, testUserId)
        }
        verify(mockUserSubscriptionDao, never()).update(any())
    }
    
    // Helper constants from SubscriptionClassifier, assuming they are not accessible otherwise.
    // If they are public/internal in actual code, direct usage is better.
    companion object {
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val STATUS_CANCELLED = "CANCELLED"
    }
}
