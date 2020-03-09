package com.icfolson.aem.groovy.console.servlets

import com.google.common.collect.ImmutableMap
import com.icfolson.aem.groovy.console.GroovyConsoleService
import com.icfolson.aem.groovy.console.api.JobProperties
import com.icfolson.aem.groovy.console.configuration.ConfigurationService
import com.icfolson.aem.groovy.console.constants.GroovyConsoleConstants
import com.icfolson.aem.groovy.console.utils.GroovyScriptUtils
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.event.jobs.JobManager
import org.apache.sling.event.jobs.ScheduledJobInfo
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static com.icfolson.aem.groovy.console.constants.GroovyConsoleConstants.DATE_CREATED
import static com.icfolson.aem.groovy.console.constants.GroovyConsoleConstants.ID
import static com.icfolson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
    "sling.servlet.paths=/bin/groovyconsole/jobs"
])
@Slf4j("LOG")
class ScheduledJobsServlet extends AbstractJsonResponseServlet {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private GroovyConsoleService consoleService

    @Reference
    private JobManager jobManager

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
        ServletException, IOException {
        def scheduledJob = findScheduledJob(request)

        if (scheduledJob) {
            writeJsonResponse(response, scheduledJob.jobProperties)
        } else {
            def scheduledJobs = jobManager.getScheduledJobs(
                GroovyConsoleConstants.JOB_TOPIC, 0, null).collect { scheduledJobInfo ->
                new ImmutableMap.Builder<String, Object>()
                    .putAll(scheduledJobInfo.jobProperties)
                    .put("scriptPreview", GroovyScriptUtils.getScriptPreview(scheduledJobInfo.jobProperties[SCRIPT] as String))
                    .put("nextExecutionDate", scheduledJobInfo.nextScheduledExecution.format(GroovyConsoleConstants.DATE_FORMAT_DISPLAY))
                    .build()
            }.sort { properties -> properties[DATE_CREATED] }

            writeJsonResponse(response, [data: scheduledJobs])
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
        ServletException, IOException {
        if (configurationService.hasScheduledJobPermission(request)) {
            def jobProperties = JobProperties.fromRequest(request)

            LOG.debug("adding job with properties : {}", jobProperties.toMap())

            if (consoleService.addScheduledJob(jobProperties)) {
                writeJsonResponse(response, jobProperties)
            } else {
                LOG.error("error adding job with properties : {}", jobProperties.toMap())

                response.status = SC_BAD_REQUEST
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    @Override
    protected void doDelete(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
        ServletException, IOException {
        if (configurationService.hasScheduledJobPermission(request)) {
            def scheduledJob = findScheduledJob(request)

            if (scheduledJob) {
                scheduledJob.unschedule()
            } else {
                response.status = SC_BAD_REQUEST
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    private ScheduledJobInfo findScheduledJob(SlingHttpServletRequest request) {
        def id = request.getParameter(ID)

        def scheduledJobInfo = null

        if (id) {
            scheduledJobInfo = jobManager.scheduledJobs.find { job -> job.jobProperties[ID] == id }
        }

        scheduledJobInfo
    }
}