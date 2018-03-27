/*
 * Copyright 2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twcable.grabbit.client.batch

import com.twcable.grabbit.DateUtil
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.launch.JobOperator
import org.springframework.context.ConfigurableApplicationContext

import javax.annotation.Nonnull

import static com.twcable.grabbit.GrabbitConfiguration.PathConfiguration

/**
 * A simple helper class that given a Application Context and initial configuration conditions, will
 * return a ClientBatchJob instance with a valid {@link JobOperator}
 */
@Slf4j
@CompileStatic
class ClientBatchJob {
    public static final String JOB_NAME = "clientJob"

    public static final String PATH = "path"
    public static final String EXCLUDE_PATHS = "excludePaths"
    public static final String WORKFLOW_CONFIGS = "workflowConfigIds"
    public static final String SCHEME = "scheme"
    public static final String HOST = "host"
    public static final String PORT = "port"
    public static final String SERVER_USERNAME = "serverUsername"
    public static final String SERVER_PASSWORD = "serverPassword"
    public static final String TRANSACTION_ID = "transactionID"
    public static final String CLIENT_USERNAME = "clientUsername"
    public static final String CONTENT_AFTER_DATE = "contentAfterDate"
    public static final String DELETE_BEFORE_WRITE = "deleteBeforeWrite"
    public static final String PATH_DELTA_CONTENT = "pathDeltaContent"
    public static final String BATCH_SIZE = "batchSize"

    private final Map<String, String> jobParameters
    private final JobOperator jobOperator


    protected ClientBatchJob(@Nonnull Map<String, String> jobParameters, @Nonnull JobOperator jobOperator) {
        if (jobParameters == null) throw new IllegalArgumentException("JobParameters == null")
        if (jobOperator == null) throw new IllegalArgumentException("jobOperator == null")

        this.jobParameters = jobParameters
        this.jobOperator = jobOperator
    }

    /**
     * Method to be called to start a job for given specific parameters
     * @return ID of the current Job's JobExecution instance
     */
    public Long start() {
        final String jobParametersString = jobParameters.collect { String key, String value ->
            "${key}=${value}"
        }.join(",")
        log.debug "Current Job Params : ${jobParametersString}"
        final Long jobExecutionId = jobOperator.start(JOB_NAME, jobParametersString)
        log.info "Kicked off job with ID : ${jobExecutionId}"
        return jobExecutionId
    }

    // **********************************************************************
    // INNER CLASSES
    // **********************************************************************

    @CompileStatic
    static class ServerBuilder {
        final ConfigurableApplicationContext configAppContext
        String scheme
        String host
        String port


        ServerBuilder(ConfigurableApplicationContext configurableApplicationContext) {
            this.configAppContext = configurableApplicationContext
        }


        CredentialsBuilder andServer(String scheme, String host, String port) {
            this.scheme = scheme
            this.host = host
            this.port = port
            return new CredentialsBuilder(this)
        }
    }

    @CompileStatic
    static class CredentialsBuilder {
        final ServerBuilder parentBuilder
        String clientUsername
        String serverUsername
        String serverPassword


        CredentialsBuilder(ServerBuilder parentBuilder) {
            this.parentBuilder = parentBuilder
        }


        JobExecutionsBuilder andCredentials(String clientUsername, String serverUsername, String serverPassword) {
            this.clientUsername = clientUsername
            this.serverUsername = serverUsername
            this.serverPassword = serverPassword
            return new JobExecutionsBuilder(this)
        }
    }

    @CompileStatic
    static class JobExecutionsBuilder {
        final CredentialsBuilder parentBuilder
        List<JobExecution> jobExecutions


        JobExecutionsBuilder(CredentialsBuilder parentBuilder) {
            this.parentBuilder = parentBuilder
        }


        ConfigurationBuilder andClientJobExecutions(List<JobExecution> jobExecutions) {
            this.jobExecutions = jobExecutions
            return new ConfigurationBuilder(this)
        }
    }

    @CompileStatic
    static class ConfigurationBuilder {
        final JobExecutionsBuilder parentBuilder
        PathConfiguration pathConfiguration
        boolean willDeleteBeforeWrite
        boolean doPathDeltaContent
        long transactionID


        ConfigurationBuilder(JobExecutionsBuilder parentBuilder) {
            this.parentBuilder = parentBuilder
        }


        Builder andConfiguration(PathConfiguration config) {
            this.pathConfiguration = config
            this.willDeleteBeforeWrite = pathConfiguration.deleteBeforeWrite
            this.doPathDeltaContent = pathConfiguration.pathDeltaContent
            return new Builder(this)
        }


        ConfigurationBuilder withTransactionID(long transactionID) {
            this.transactionID = transactionID
            return this
        }
    }

    @CompileStatic
    static class Builder {
        final ConfigurationBuilder configsBuilder
        final PathConfiguration pathConfiguration
        final CredentialsBuilder credentialsBuilder
        final JobExecutionsBuilder jobExecutionsBuilder
        final ServerBuilder serverBuilder


        Builder(ConfigurationBuilder parentBuilder) {
            this.configsBuilder = parentBuilder
            this.pathConfiguration = configsBuilder.pathConfiguration
            this.jobExecutionsBuilder = configsBuilder.parentBuilder
            this.credentialsBuilder = jobExecutionsBuilder.parentBuilder
            this.serverBuilder = credentialsBuilder.parentBuilder
        }


        ClientBatchJob build() {
            final jobParameters = [
                "timestamp"              : System.currentTimeMillis() as String,
                (PATH)                : pathConfiguration.path,
                (SCHEME)              : serverBuilder.scheme,
                (HOST)                : serverBuilder.host,
                (PORT)                : serverBuilder.port,
                (CLIENT_USERNAME)     : credentialsBuilder.clientUsername,
                (SERVER_USERNAME)     : credentialsBuilder.serverUsername,
                (SERVER_PASSWORD)     : credentialsBuilder.serverPassword,
                (TRANSACTION_ID)      : String.valueOf(configsBuilder.transactionID),
                (EXCLUDE_PATHS)       : pathConfiguration.excludePaths.join("*"),
                (WORKFLOW_CONFIGS)    : pathConfiguration.workflowConfigIds.join("|"),
                (DELETE_BEFORE_WRITE) : String.valueOf(pathConfiguration.deleteBeforeWrite),
                (PATH_DELTA_CONTENT)  : String.valueOf(pathConfiguration.pathDeltaContent),
                (BATCH_SIZE)          : String.valueOf(pathConfiguration.batchSize)
            ] as Map<String, String>

            if (pathConfiguration.pathDeltaContent) {
                final lastSuccessFulJobExecution = jobExecutionsBuilder.jobExecutions?.find {
                    it.jobParameters.getString(PATH) == pathConfiguration.path && (it.status == BatchStatus.COMPLETED)
                }
                if (lastSuccessFulJobExecution) {
                    final contentAfterDate = DateUtil.getISOStringFromDate(lastSuccessFulJobExecution.endTime)
                    log.info "Last Successful run for ${pathConfiguration.path} was on $contentAfterDate"
                    return new ClientBatchJob(
                        jobParameters + ([(CONTENT_AFTER_DATE): contentAfterDate] as Map<String, String>),
                        serverBuilder.configAppContext.getBean("clientJobOperator", JobOperator)
                    )
                }
                else {
                    log.warn "There was no successful job run for $pathConfiguration.path. Defaulting to normal content grab"
                }
            }
            return new ClientBatchJob(
                jobParameters,
                serverBuilder.configAppContext.getBean("clientJobOperator", JobOperator)
            )
        }
    }
}
