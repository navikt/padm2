package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import java.sql.Connection
import java.sql.ResultSet
import no.nav.syfo.Environment
import org.flywaydb.core.Flyway

class Database(
    private val env: Environment
) : DatabaseInterface {
    private val dataSource: HikariDataSource

    override val connection: Connection
        get() = dataSource.connection

    init {
        runFlywayMigrations()

        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.jdbcUrl()
                username = env.databaseUsername
                password = env.databasePassword
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = 10001
                maxLifetime = 300000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                metricsTrackerFactory = PrometheusMetricsTrackerFactory()
                validate()
            }
        )
    }

    private fun runFlywayMigrations() = Flyway.configure().run {
        dataSource(env.jdbcUrl(), env.databaseUsername, env.databasePassword)
        load().migrate().migrationsExecuted
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

interface DatabaseInterface {
    val connection: Connection
}
