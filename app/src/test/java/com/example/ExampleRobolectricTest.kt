package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ReminderRepository
import com.example.model.USER_MAYA
import com.example.model.USER_MICHEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lembrete Inteligente", appName)
    }

    @Test
    fun `verify strict user data isolation between Michel and Maya`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ReminderRepository.getInstance(context)

        val michelReminders = repository.getRemindersForUser(USER_MICHEL.uid)
        val mayaReminders = repository.getRemindersForUser(USER_MAYA.uid)

        // Ensure users have their own isolated lists
        assertTrue(michelReminders.isNotEmpty())
        assertTrue(mayaReminders.isNotEmpty())

        // Ensure Michel's items belong only to Michel
        for (item in michelReminders) {
            assertEquals(USER_MICHEL.uid, item.userId)
            assertNotEquals(USER_MAYA.uid, item.userId)
        }

        // Ensure Maya's items belong only to Maya
        for (item in mayaReminders) {
            assertEquals(USER_MAYA.uid, item.userId)
            assertNotEquals(USER_MICHEL.uid, item.userId)
        }
    }
}
