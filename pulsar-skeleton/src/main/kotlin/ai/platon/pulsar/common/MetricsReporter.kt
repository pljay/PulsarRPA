/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.platon.pulsar.common

import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.ImmutableConfig
import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.mapreduce.TaskInputOutputContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TODO: Use com.codahale.metrics.MetricRegistry or Spark Metrics System, or something similar
 */
class MetricsReporter(
        private val jobName: String,
        private val counter: MetricsCounters,
        private val conf: ImmutableConfig,
        private val context: TaskInputOutputContext<*, *, *, *>
) : Thread() {
    private val running = AtomicBoolean(false)
    private val silent = AtomicBoolean(false)
    var log = LOG_NON_ADDITIVITY
    private val reportInterval = conf.getDuration(CapabilityTypes.REPORTER_REPORT_INTERVAL, Duration.ofSeconds(10))

    init {
        val name = "Reporter-" + counter.id()
        setName(name)
        isDaemon = true
        startReporter()
    }

    fun silent() {
        silent.set(true)
    }

    fun startReporter() {
        if (!running.get()) {
            start()
            running.set(true)
        }
    }

    fun stopReporter() {
        running.set(false)
        silent.set(false)
        try {
            join()
        } catch (e: InterruptedException) {
            log.error(e.toString())
        }
    }

    override fun run() {
        val outerBorder = StringUtils.repeat('-', 100)
        val innerBorder = StringUtils.repeat('.', 100)
        log.info(outerBorder)
        log.info(innerBorder)
        log.info("== Reporter started [ " + DateTimeUtil.now() + " ] [ " + jobName + " ] ==")
        log.debug("All registered counters : " + counter.registeredCounters)
        do {
            try {
                sleep(reportInterval.toMillis())
            } catch (ignored: InterruptedException) {
            }
            counter.accumulateGlobalCounters(context)
            report()
        } while (running.get())
        log.info("== Reporter stopped [ " + DateTimeUtil.now() + " ] ==")
    } // run

    private fun report() {
        // Can only access variables in this thread
        if (!silent.get()) {
            val status = counter.getStatus(false)
            if (status.isNotEmpty()) {
                log.info(status)
            }
        }
    }

    companion object {
        val LOG_ADDITIVITY = LoggerFactory.getLogger(MetricsReporter::class.java.toString() + "Add")
        val LOG_NON_ADDITIVITY = LoggerFactory.getLogger(MetricsReporter::class.java)
    }
}