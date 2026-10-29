package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ReminderRepository
import com.example.model.Reminder
import com.example.model.UserProfile
import com.lembreteinteligente.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun `verify user session management and clean default state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ReminderRepository.getInstance(context)

        // Ensure user can be set and logged out cleanly
        val customUser = UserProfile(
            uid = "user_custom_test_123",
            name = "Carlos Silva",
            email = "carlos@example.com"
        )
        repository.setCurrentUser(customUser)
        assertEquals("Carlos Silva", repository.getCurrentUser()?.name)
        assertEquals("carlos@example.com", repository.getCurrentUser()?.email)

        repository.setCurrentUser(null)
        assertNull(repository.getCurrentUser())
    }

    @Test
    fun `verify strict user data isolation between custom accounts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ReminderRepository.getInstance(context)

        val userAId = "usr_account_a"
        val userBId = "usr_account_b"

        // Initially no default pre-filled items
        val initialA = repository.getRemindersForUser(userAId)
        assertTrue(initialA.isEmpty())

        // Add reminder for User A
        val reminderA = Reminder(
            id = "rem_a_1",
            userId = userAId,
            title = "Consulta Médica",
            category = "Saúde",
            scheduledEpochMs = System.currentTimeMillis() + 3600000
        )
        repository.addReminder(reminderA)

        // Add reminder for User B
        val reminderB = Reminder(
            id = "rem_b_1",
            userId = userBId,
            title = "Pagar aluguel",
            category = "Finanças",
            scheduledEpochMs = System.currentTimeMillis() + 7200000
        )
        repository.addReminder(reminderB)

        val listA = repository.getRemindersForUser(userAId)
        val listB = repository.getRemindersForUser(userBId)

        assertEquals(1, listA.size)
        assertEquals(1, listB.size)
        assertEquals("Consulta Médica", listA.first().title)
        assertEquals("Pagar aluguel", listB.first().title)
        assertNotEquals(listA.first().userId, listB.first().userId)
    }

    @Test
    fun `verify precision of Portuguese voice command time parsing`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.ReminderViewModel(app)

        // Test 1: "Reunião amanhã às 10 horas"
        val cmd1 = vm.parsePortugueseCommandForTesting("Reunião com equipe amanhã às 10 horas")
        assertEquals(10, cmd1.detectedHour)
        assertEquals(0, cmd1.detectedMinute)
        assertTrue(cmd1.isTomorrow)
        assertEquals("Trabalho", cmd1.category)

        // Test 2: "Tomar remédio hoje às 14 horas e 30 minutos"
        val cmd2 = vm.parsePortugueseCommandForTesting("Me lembre de tomar remédio hoje às 14 horas e 30 minutos")
        assertEquals(14, cmd2.detectedHour)
        assertEquals(30, cmd2.detectedMinute)
        assertEquals("Saúde", cmd2.category)
        assertTrue(cmd2.cleanTitle.contains("remédio", ignoreCase = true))

        // Test 3: "Pagar conta às 8 da noite" -> 20:00
        val cmd3 = vm.parsePortugueseCommandForTesting("Pagar conta de luz às 8 da noite")
        assertEquals(20, cmd3.detectedHour)
        assertEquals(0, cmd3.detectedMinute)
        assertEquals("Finanças", cmd3.category)

        // Test 4: "Aula de inglês às 3 da tarde" -> 15:00
        val cmd4 = vm.parsePortugueseCommandForTesting("Aula de inglês às 3 da tarde")
        assertEquals(15, cmd4.detectedHour)
        assertEquals(0, cmd4.detectedMinute)
        assertEquals("Estudos", cmd4.category)

        // Test 5: "Dentista amanhã às 15h30" -> 15:30 tomorrow
        val cmd5 = vm.parsePortugueseCommandForTesting("Consulta no dentista amanhã às 15h30")
        assertEquals(15, cmd5.detectedHour)
        assertEquals(30, cmd5.detectedMinute)
        assertTrue(cmd5.isTomorrow)
        assertEquals("Saúde", cmd5.category)
    }
}
