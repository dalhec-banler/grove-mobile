package io.nativeplanet.grove

import android.app.Application
import io.nativeplanet.grove.data.local.GroveDatabase
import io.nativeplanet.grove.data.remote.UrbitClient
import io.nativeplanet.grove.data.repository.GroveRepository

class GroveApp : Application() {

    lateinit var database: GroveDatabase
        private set

    lateinit var urbitClient: UrbitClient
        private set

    lateinit var repository: GroveRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = GroveDatabase.getInstance(this)
        urbitClient = UrbitClient()
        repository = GroveRepository(this, urbitClient, database)
    }

    companion object {
        lateinit var instance: GroveApp
            private set
    }
}
