package lol.djungelskog.hcbx

import android.app.Application
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.Auth
import lol.djungelskog.hcbx.data.Settings

class HcbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Auth.init(this)
        Api.init(this)
    }
}
