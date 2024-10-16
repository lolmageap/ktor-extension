package extension.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration

class ShedlockTestStringSpec : StringSpec({
    beforeTest {
        embeddedServer(Netty).start()

        val dataSource = HikariDataSource(
            HikariConfig().also {
                it.jdbcUrl = "jdbc:postgresql://localhost:5432/shedlock"
                it.username = "postgres"
                it.password = "postgres"
                it.validate()
            }
        )

        Database.connect(dataSource)
        transaction { SchemaUtils.create(Shedlocks) }
    }

    afterEach {
        transaction { SchemaUtils.drop(Shedlocks) }
        transaction { SchemaUtils.create(Shedlocks) }
    }

    afterTest {
        transaction { SchemaUtils.drop(Shedlocks) }
    }

    "Verify that shedlock ensures the logic is executed only once when multiple applications start simultaneously with no existing data in the database" {
        val name = "test"
        val duration = 1.toMinutes()
        var count = 0

        coroutineScope {
            launch { runCatching { shedlock(name, duration) { count += 1 } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
        }

        count shouldBe 1
    }

    "Verify that shedlock ensures the logic is executed only once when multiple applications start simultaneously with existing data in the database" {
        val name = "test"
        val duration = 1.toSeconds()
        var count = 0

        shedlock(name, duration) { count += 1 }
        delay(1000)

        coroutineScope {
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
            repeat(50) { launch { runCatching { shedlock(name, duration) { count += 1 } } } }
        }

        count shouldBe 2
    }
})

private fun Int.toMinutes(): Duration = Duration.ofMinutes(this.toLong())
private fun Int.toSeconds(): Duration = Duration.ofSeconds(this.toLong())