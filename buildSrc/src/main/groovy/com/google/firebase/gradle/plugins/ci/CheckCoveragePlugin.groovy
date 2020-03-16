// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins.ci

import com.google.firebase.gradle.plugins.measurement.MetricsReportUploader

import static com.google.firebase.gradle.plugins.measurement.MetricsServiceApi.Metric
import static com.google.firebase.gradle.plugins.measurement.MetricsServiceApi.Result
import static com.google.firebase.gradle.plugins.measurement.MetricsServiceApi.Report

import com.google.firebase.gradle.plugins.measurement.TestLogFinder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

class CheckCoveragePlugin implements Plugin<Project> {
    private final XmlSlurper parser

    CheckCoveragePlugin() {
        this.parser = new XmlSlurper()
        this.parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        this.parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }

    @Override
    void apply(Project project) {
        project.configure(project.subprojects) {
            apply plugin: 'jacoco'

            jacoco {
                toolVersion = '0.8.5'
            }

            task('checkCoverage', type: JacocoReport) {
                dependsOn 'check'
                description 'Generates check coverage report and uploads to Codecov.io.'
                group 'verification'

                def excludes = [
                        '**/R.class',
                        '**/R$*.class',
                        '**/BuildConfig.*',
                        '**/proto/**',
                        '**Manifest*.*'
                ]
                classDirectories = files([
                        fileTree(dir: "$buildDir/intermediates/javac/release", excludes: excludes),
                        fileTree(dir: "$buildDir/tmp/kotlin-classes/release", excludes: excludes),
                ])
                sourceDirectories = files(['src/main/java', 'src/main/kotlin'])
                executionData = fileTree(dir: "$buildDir", includes: ['jacoco/*.exec'])
                reports {
                    html.enabled true
                    xml.enabled true
                }

                outputs.upToDateWhen { false }

                doFirst {
                    logger.quiet("Reports directory: ${it.project.jacoco.reportsDir}")
                }

                doLast {
                    upload it
                }
            }

            tasks.withType(Test) {
                jacoco.includeNoLocationClasses true
            }

        }
    }

    private def parseCoverageReport(path) {
        def log = TestLogFinder.generateCurrentLogLink()
        def report = new Report(Metric.Coverage, [], log)

        def xmlReport = this.parser.parse(path)
        def sdk = xmlReport.@name.text()
        def sources = xmlReport.package.sourcefile
        for (def src : sources) {
            def coverage = src.counter.find { it.@type == 'LINE' }
            def covered = Double.parseDouble(coverage.@covered.text())
            def missed = Double.parseDouble(coverage.@missed.text())
            def percent = covered / (covered + missed)

            def result = new Result(sdk, )
        }
        def lineCoverage = xmlReport.counter.find { it.@type == 'LINE' }
        def covered = Double.parseDouble(lineCoverage.@covered.text())
        def missed = Double.parseDouble(lineCoverage.@missed.text())
        def percent = covered / (covered + missed)

        def result = new Result(name, "line", percent)
        def log = TestLogFinder.generateCurrentLogLink()
        def report = new Report(Metric.Coverage, [result], log)

        return report
    }

    private def upload(task) {
        def xmlReportPath = task.reports.xml.destination
        def report = parseCoverageReport(xmlReportPath)
        new File(task.project.buildDir, 'coverage.json').withWriter {
            it.write(report.toJson())
        }

        MetricsReportUploader.upload(task.project, "${task.project.buildDir}/coverage.json")
    }
}
