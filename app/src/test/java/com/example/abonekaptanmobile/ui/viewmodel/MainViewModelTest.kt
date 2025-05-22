package com.example.abonekaptanmobile.ui.viewmodel

import android.content.Context
import com.example.abonekaptanmobile.auth.GoogleAuthManager
import com.example.abonekaptanmobile.data.local.dao.UserSubscriptionDao
import com.example.abonekaptanmobile.data.local.entity.UserSubscriptionEntity
import com.example.abonekaptanmobile.data.repository.FeedbackRepository
import com.example.abonekaptanmobile.data.repository.GmailRepository
import com.example.abonekaptanmobile.model.SubscriptionItem
import com.example.abonekaptanmobile.model.SubscriptionStatus
import com.example.abonekaptanmobile.services.SubscriptionClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import java.lang.reflect.Method

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MainViewModelTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockGoogleAuthManager: GoogleAuthManager

    @Mock
    private lateinit var mockGmailRepository: GmailRepository

    @Mock
    private lateinit var mockSubscriptionClassifier: SubscriptionClassifier

    @Mock
    private lateinit var mockFeedbackRepository: FeedbackRepository

    @Mock
    private lateinit var mockUserSubscriptionDao: UserSubscriptionDao

    private lateinit var viewModel: MainViewModel
    private lateinit var mapEntitiesMethod: Method


    @Before
    fun setUp() {
        viewModel = MainViewModel(
            mockContext,
            mockGoogleAuthManager,
            mockGmailRepository,
            mockSubscriptionClassifier,
            mockFeedbackRepository,
            mockUserSubscriptionDao
        )
        // Prepare the private method for testing
        mapEntitiesMethod = MainViewModel::class.java.getDeclaredMethod("mapEntitiesToSubscriptionItems", List::class.java).apply {
            isAccessible = true
        }
    }

    @Test
    fun `testMapEntitiesToSubscriptionItems_activeEntity_mapsCorrectly`() {
        val currentTime = System.currentTimeMillis()
        val activeEntity = UserSubscriptionEntity(
            id = 1L,
            serviceName = "Netflix",
            userId = "user1",
            subscriptionStartDate = currentTime - 100000,
            subscriptionEndDate = null,
            status = "ACTIVE",
            lastEmailIdProcessed = "email123",
            lastActiveConfirmationDate = currentTime - 5000,
            createdAt = currentTime - 200000,
            updatedAt = currentTime - 5000
        )
        val entities = listOf(activeEntity)

        @Suppress("UNCHECKED_CAST")
        val resultItems = mapEntitiesMethod.invoke(viewModel, entities) as List<SubscriptionItem>
        
        assertNotNull(resultItems)
        assertEquals(1, resultItems.size)
        val item = resultItems[0]

        assertEquals("Netflix", item.serviceName)
        assertEquals(SubscriptionStatus.ACTIVE, item.status)
        assertEquals(activeEntity.subscriptionStartDate, item.subscriptionStartDate)
        assertEquals(activeEntity.lastActiveConfirmationDate, item.lastEmailDate) // For ACTIVE, lastEmailDate is lastActiveConfirmationDate
        assertNull(item.cancellationDate)
        assertEquals(0, item.emailCount) // Placeholder
        assertTrue(item.relatedEmailIds.isEmpty()) // Placeholder
    }

    @Test
    fun `testMapEntitiesToSubscriptionItems_cancelledEntity_mapsCorrectly`() {
        val currentTime = System.currentTimeMillis()
        val cancelledEndDate = currentTime - 10000
        val cancelledEntity = UserSubscriptionEntity(
            id = 2L,
            serviceName = "Spotify",
            userId = "user2",
            subscriptionStartDate = currentTime - 200000,
            subscriptionEndDate = cancelledEndDate,
            status = "CANCELLED",
            lastEmailIdProcessed = "email456",
            lastActiveConfirmationDate = currentTime - 15000, // This might be older than endDate
            createdAt = currentTime - 300000,
            updatedAt = currentTime - 10000 // Assume updated when cancelled
        )
        val entities = listOf(cancelledEntity)

        @Suppress("UNCHECKED_CAST")
        val resultItems = mapEntitiesMethod.invoke(viewModel, entities) as List<SubscriptionItem>

        assertNotNull(resultItems)
        assertEquals(1, resultItems.size)
        val item = resultItems[0]

        assertEquals("Spotify", item.serviceName)
        assertEquals(SubscriptionStatus.CANCELLED, item.status)
        assertEquals(cancelledEntity.subscriptionStartDate, item.subscriptionStartDate)
        assertEquals(cancelledEntity.subscriptionEndDate, item.cancellationDate)
        assertEquals(cancelledEntity.subscriptionEndDate, item.lastEmailDate) // For CANCELLED, lastEmailDate is subscriptionEndDate
        assertEquals(0, item.emailCount)
        assertTrue(item.relatedEmailIds.isEmpty())
    }
    
    @Test
    fun `testMapEntitiesToSubscriptionItems_unknownStatus_mapsToUnknown`() {
        val currentTime = System.currentTimeMillis()
        val unknownStatusEntity = UserSubscriptionEntity(
            id = 3L,
            serviceName = "MysteryService",
            userId = "user3",
            subscriptionStartDate = currentTime - 100000,
            status = "PENDING_APPROVAL", // An unexpected status
            lastEmailIdProcessed = "email789",
            lastActiveConfirmationDate = currentTime - 5000,
            createdAt = currentTime - 200000,
            updatedAt = currentTime - 5000
        )
        val entities = listOf(unknownStatusEntity)

        @Suppress("UNCHECKED_CAST")
        val resultItems = mapEntitiesMethod.invoke(viewModel, entities) as List<SubscriptionItem>
        
        assertNotNull(resultItems)
        assertEquals(1, resultItems.size)
        val item = resultItems[0]
        
        assertEquals("MysteryService", item.serviceName)
        assertEquals(SubscriptionStatus.UNKNOWN, item.status) // Should map to UNKNOWN
        assertEquals(unknownStatusEntity.lastActiveConfirmationDate, item.lastEmailDate) // Default behavior for non-cancelled
    }

    @Test
    fun `testMapEntitiesToSubscriptionItems_cancelledEntityWithNullEndDate_mapsLastEmailDateToUpdatedAt`() {
        val currentTime = System.currentTimeMillis()
        // This case might happen if cancellation is marked but end date processing failed or is pending
        val cancelledEntityNullEnd = UserSubscriptionEntity(
            id = 4L,
            serviceName = "Adobe",
            userId = "user4",
            subscriptionStartDate = currentTime - 200000,
            subscriptionEndDate = null, // EndDate is null
            status = "CANCELLED",
            lastEmailIdProcessed = "emailABC",
            lastActiveConfirmationDate = currentTime - 15000,
            createdAt = currentTime - 300000,
            updatedAt = currentTime - 8000 // Updated when status changed to CANCELLED
        )
        val entities = listOf(cancelledEntityNullEnd)

        @Suppress("UNCHECKED_CAST")
        val resultItems = mapEntitiesMethod.invoke(viewModel, entities) as List<SubscriptionItem>

        assertNotNull(resultItems)
        assertEquals(1, resultItems.size)
        val item = resultItems[0]

        assertEquals("Adobe", item.serviceName)
        assertEquals(SubscriptionStatus.CANCELLED, item.status)
        assertNull(item.cancellationDate) // subscriptionEndDate is null
        assertEquals(cancelledEntityNullEnd.updatedAt, item.lastEmailDate) // For CANCELLED with null endDate, lastEmailDate is updatedAt
    }

    @Test
    fun `testMapEntitiesToSubscriptionItems_emptyList_returnsEmptyList`() {
        val entities = emptyList<UserSubscriptionEntity>()
        
        @Suppress("UNCHECKED_CAST")
        val resultItems = mapEntitiesMethod.invoke(viewModel, entities) as List<SubscriptionItem>
        
        assertNotNull(resultItems)
        assertTrue(resultItems.isEmpty())
    }
}
