package com.walkietalkie.dictationime

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.walkietalkie.dictationime.settings.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun settingsScreenRendersCoreControls() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.modelStatusText)).check(matches(isDisplayed()))
        onView(withId(R.id.grantPermissionButton)).check(matches(isDisplayed()))
        onView(withId(R.id.openImeSettingsButton)).check(matches(isDisplayed()))
        onView(withId(R.id.reinitializeModelButton)).check(matches(isDisplayed()))
    }
}
