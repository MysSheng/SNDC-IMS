package icdf.sndc.ims

import android.app.Application
import icdf.sndc.ims.data.AppContainer

/** 應用程式進入點；以簡單的手動依賴注入（AppContainer）建立共用物件。 */
class SndcApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
