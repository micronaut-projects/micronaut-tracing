package io.micronaut.tracing.util

import spock.lang.Specification

class MethodNameFormatterSpec extends Specification {

    void 'formats Kotlin mangled method names'() {
        expect:
        MethodNameFormatter.format(methodName) == formattedName

        where:
        methodName                         | formattedName
        'defaultSpan-Aa0_--Z'              | 'defaultSpan'
        'defaultSpan-longerHash'           | 'defaultSpan'
        'defaultSpan-longerHash$default'   | 'defaultSpan'
        'defaultSpan-short'                | 'defaultSpan-short'
        'defaultSpan-hash$other'           | 'defaultSpan-hash$other'
        'defaultSpan-hash.with.dot'        | 'defaultSpan-hash.with.dot'
        'defaultSpan-hash{invalid'         | 'defaultSpan-hash{invalid'
        'defaultSpan-hash:invalid'         | 'defaultSpan-hash:invalid'
        'defaultSpan-longer-Hash$default'  | 'defaultSpan'
        'defaultSpan-'                     | 'defaultSpan-'
        'defaultSpan-$default'             | 'defaultSpan-$default'
        '-leadingSeparator'                | '-leadingSeparator'
        'hyphen-name'                      | 'hyphen-name'
        'plainMethod'                      | 'plainMethod'
    }
}
