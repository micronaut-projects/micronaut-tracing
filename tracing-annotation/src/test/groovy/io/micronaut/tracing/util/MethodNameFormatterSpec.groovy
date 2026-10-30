package io.micronaut.tracing.util

import spock.lang.Specification

class MethodNameFormatterSpec extends Specification {

    void 'formats Kotlin mangled method names'() {
        expect:
        MethodNameFormatter.format(methodName) == formattedName

        where:
        methodName                         | formattedName
        'defaultSpan-Aa0_--Z'              | 'defaultSpan'
        'defaultSpan-d1pmJ48'              | 'defaultSpan'
        'defaultSpan-d1pmJ48$default'      | 'defaultSpan'
        'defaultSpan-abcdefg'              | 'defaultSpan'
        'defaultSpan-abc-efg$default'      | 'defaultSpan'
        'defaultSpan-longerHash'           | 'defaultSpan-longerHash'
        'defaultSpan-short'                | 'defaultSpan-short'
        'defaultSpan-hash$other'           | 'defaultSpan-hash$other'
        'defaultSpan-hash.wit'             | 'defaultSpan-hash.wit'
        'defaultSpan-hash{in'              | 'defaultSpan-hash{in'
        'defaultSpan-hash:in'              | 'defaultSpan-hash:in'
        'defaultSpan-'                     | 'defaultSpan-'
        'defaultSpan-$default'             | 'defaultSpan-$default'
        '-leadingSeparator'                | '-leadingSeparator'
        '-abcdefg'                         | '-abcdefg'
        'hyphen-name'                      | 'hyphen-name'
        'long-hyphenated-backtick-name'    | 'long-hyphenated-backtick-name'
        'plainMethod'                      | 'plainMethod'
    }
}
