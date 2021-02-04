/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.resolve.locking

import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Unroll

abstract class AbstractLockingIntegrationTest extends AbstractDependencyResolutionTest {
    def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)
    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'depLock'"
        resolve = new ResolveTestFixture(buildFile, "lockedConf")
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
    }

    abstract LockMode lockMode()

    @Unroll
    def 'succeeds when lock file does not conflict from declared versions (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'], unique)

        def constraintVersion = lockMode() == LockMode.LENIENT ? "1.0" : "{strictly 1.0}"
        def extraReason = lockMode() == LockMode.LENIENT ? " (update/lenient mode)" : ""

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(":", ":depLock:") {
                edge("org:foo:1.+", "org:foo:1.0")
                constraint("org:foo:$constraintVersion", "org:foo:1.0") {
                    byConstraint("dependency was locked to version '1.0'$extraReason")
                }
            }
        }

        where:
        unique << [true, false]
    }

    @ToBeFixedForConfigurationCache
    def 'does not write-locks for not locked configuration'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    unlockedConf
}

dependencies {
    unlockedConf 'org:foo:1.+'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.expectLockStateMissing('unlockedConf')
    }

    @ToBeFixedForConfigurationCache
    def 'writes dependency lock file when requested'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        when:
        succeeds'dependencies', '--write-locks', '--refresh-dependencies'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'])
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def "writes dependency lock file for resolved version #version"() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'bar', '2.0').publish()
        mavenRepo.module('org', 'bar', '2.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
    subConf
}

dependencies {
    lockedConf 'org:bar:${version}'
    subConf 'org:bar:1.1'
}
"""

        when:
        succeeds'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile(['lockedConf': ["org:bar:${resolved}"], 'subConf': ['org:bar:1.1']])

        where:
        version     | resolved
        "[1.0,)"    | "2.1"
        "[1.0,2.0)" | "1.1"
        "[1.0,2.0]" | "2.0"
        "(,2.0)"    | "1.1"
        "1.+"       | "1.1"
        "+"         | "2.1"
    }

    @ToBeFixedForConfigurationCache
    def "does not lock a configuration that is marked with deactivateDependencyLocking"() {
        ['foo', 'foz', 'bar', 'baz'].each { artifact ->
            mavenRepo.module('org', artifact, '1.0').publish()
            mavenRepo.module('org', artifact, '1.1').publish()
            mavenRepo.module('org', artifact, '1.2').publish()
            mavenRepo.module('org', artifact, '2.0').publish()
        }

        buildFile << """
dependencyLocking {
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        url '${mavenRepo.uri}'
    }
}

dependencyLocking {
    lockAllConfigurations()
}

configurations {
    conf
    lockEnabledConf {
        extendsFrom conf
    }
     secondLockEnabledConf {
        extendsFrom lockEnabledConf
        resolutionStrategy.deactivateDependencyLocking()
    }
}
dependencies {
    conf 'org:foo:[1.0,)'
    conf 'org:foo:1.1'

    conf 'org:foz:latest.integration'
    conf 'org:foz:1.1'

    conf 'org:bar:1.+'
    conf 'org:bar:1.1'

    conf 'org:baz:+'
    conf 'org:baz:1.1'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile([lockEnabledConf: ['org:bar:1.2', 'org:baz:2.0', 'org:foo:1.1', 'org:foz:2.0'], conf: ['org:bar:1.2', 'org:baz:2.0', 'org:foo:1.1', 'org:foz:2.0']])
        lockfileFixture.expectLockStateMissing('secondLockEnabledConf')
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'upgrades lock file (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'foo', '2.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""


        lockfileFixture.createLockfile('lockedConf', ["org:foo:1.0"], unique)

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1'])

        where:
        unique << [true, false]
    }

    @ToBeFixedForConfigurationCache
    def 'does not write duplicates in the lockfile'() {
        def foo = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').dependsOn(foo).publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'])
    }

    @ToBeFixedForConfigurationCache
    def 'includes transitive dependencies in the lock file'() {
        def dep = mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.0').dependsOn(dep).publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        when:
        succeeds 'dependencies', '--configuration', 'lockedConf', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'])
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'updates part of the lockfile (initial unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'], unique)

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
    lockedConf 'org:bar:[1.0,2.0)'
}
"""

        when:
        succeeds 'dependencies', '--update-locks', 'org:foo'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1', 'org:bar:1.0'])

        where:
        unique << [true, false]
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'updates part of the lockfile using wildcard (initial unique #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'], unique)

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
    lockedConf 'org:bar:[1.0,2.0)'
}
"""

        when:
        succeeds 'dependencies', '--update-locks', 'org:f*'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1', 'org:bar:1.0'])

        where:
        unique << [true, false]
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'updates but ignores irrelevant modules (initial unique #unique)'() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0', 'org:foo:1.0'], unique)

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:bar:[1.0,2.0)'
    lockedConf 'org:foo:[1.0,2.0)'
}
"""

        when:
        succeeds 'dependencies', '--update-locks', 'org:foo,org:baz'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:bar:1.0', 'org:foo:1.1'])

        where:
        unique << [true, false]
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'updates multiple parts of the lockfile (initial unique #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'buz', '1.0').publish()
        mavenRepo.module('org', 'buz', '1.1').publish()
        def bar10 = mavenRepo.module('org', 'bar', '1.0').publish()
        def bar11 = mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'baz', '1.0').dependsOn(bar10).publish()
        mavenRepo.module('org', 'baz', '1.1').dependsOn(bar11).publish()

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0', 'org:baz:1.0', 'org:buz:1.0'], unique)

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
    lockedConf 'org:baz:[1.0,2.0)'
    lockedConf 'org:buz:[1.0,2.0)'
}
"""

        when:
        succeeds 'dependencies', '--update-locks', 'org:foo,org:baz'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1', 'org:bar:1.1', 'org:baz:1.1', 'org:buz:1.0'])

        where:
        unique << [true, false]
    }

    @ToBeFixedForConfigurationCache
    def 'writes an empty lock file for an empty configuration'() {
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}
"""
        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', [])
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'overwrites a not empty lock file with an empty one when configuration no longer has dependencies (unique: #unique)'() {
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}
"""
        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', [])

        where:
        unique << [true, false]
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "fails if trying to resolve a locked configuration with #flag"() {
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

configurations {
    lockedConf {
        resolutionStrategy {
            $flag()
        }
    }
}

dependencies {
    lockedConf 'org:foo:1.0'
}
"""

        when:
        fails 'dependencies'

        then:
        failure.assertHasCause "Resolution strategy has both dependency locking and fail on $desc versions enabled. You must choose between the two modes."

        where:
        flag                              | desc
        'failOnDynamicVersions'           | 'dynamic'
        'failOnChangingVersions'          | 'changing'
        'failOnNonReproducibleResolution' | 'dynamic'
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def "does not write ignored dependencies to lock file (notation #notation)"() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
    ignoredDependencies.add('$notation')
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        when:
        succeeds'dependencies', '--write-locks', '--refresh-dependencies'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:bar:1.0'])

        where:
        notation << ['org:foo', '*:foo', 'or*:f*']
    }

    @ToBeFixedForConfigurationCache
    @Unroll
    def 'updates part of the lockfile, with ignored dependencies (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'baz', '1.0').publish()
        mavenRepo.module('org', 'baz', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0', 'org:bar:1.0'], unique)

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
    ignoredDependencies.add('org:baz')
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
    lockedConf 'org:bar:[1.0,2.0)'
    lockedConf 'org:baz:[1.0,2.0)'
}
"""

        when:
        succeeds 'dependencies', '--update-locks', 'org:foo'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1', 'org:bar:1.0'])

        where:
        unique << [true, false]
    }

}
