package graphql.parser

import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.StringValue
import spock.lang.Specification

class UnicodeUtilParserTest extends Specification {
    /**
     * Implements RFC to support full Unicode https://github.com/graphql/graphql-spec/pull/849
     *
     * Key changes
     * + SourceCharacters now include all Unicode scalar values. Previously only included up to U+FFFF (Basic Multilingual Plane).
     * + SourceCharacters now include control characters. Previously certain control characters were excluded.
     * + Surrogate pair validation added.
     *
     * Note that "unescaped" Unicode characters such as 🍺 are handled by ANTLR grammar.
     * "Escaped" Unicode characters such as \\u{1F37A} are handled by StringValueParsing.
     */

    // With this RFC, escaped code points outside the Basic Multilingual Plane (e.g. emojis) can be parsed.
    def "parsing beer stein as escaped unicode"() {
        given:
        def input = '''"\\u{1F37A} hello"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''🍺 hello''' // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "parsing beer stein without escaping"() {
        given:
        def input = '''"🍺 hello"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''🍺 hello''' // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "allow braced escaped unicode"() {
        given:
        def input = '''
              {
              foo(arg: "\\u{1F37A}")
               }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)
        def field = operationDefinition.getSelectionSet().getSelections()[0] as Field
        def argValue = field.arguments[0].value as StringValue

        then:
        argValue.getValue() == "🍺" // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    /**
     * From the RFC:
     * For legacy reasons, a *supplementary character* may be escaped by two
     * fixed-width unicode escape sequences forming a *surrogate pair*. For example
     * the input `"\\uD83D\\uDCA9"` is a valid {StringValue} which represents the same
     * Unicode text as `"\\u{1F4A9}"`. While this legacy form is allowed, it should be
     * avoided as a variable-width unicode escape sequence is a clearer way to encode
     * such code points.
     */
    def "allow surrogate pairs escaped unicode"() {
        given:
        def input = '''
              {
              foo(arg: "\\ud83c\\udf7a")
               }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)
        def field = operationDefinition.getSelectionSet().getSelections()[0] as Field
        def argValue = field.arguments[0].value as StringValue

        then:
        argValue.getValue() == "🍺" // contains the beer icon U+1F37 A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    /**
     * Valid surrogate pair combinations (from the RFC):
     * + If {leadingValue} is >= 0xD800 and <= 0xDBFF (a *Leading Surrogate*):
     * + Assert {trailingValue} is >= 0xDC00 and <= 0xDFFF (a *Trailing Surrogate*).
     */
    def "invalid surrogate pair - no trailing value"() {
        given:
        def input = '''"\\uD83D hello"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - end of string"() {
        given:
        def input = '''"\\uD83D"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid trailing value"() {
        given:
        def input = '''"\\uD83D\\uDBFF"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - no leading value"() {
        given:
        def input = '''"\\uDC00"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid leading value"() {
        given:
        def input = '''"\\uD700\\uDC00"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "valid surrogate pair - leading code with braces"() {
        given:
        def input = '''"hello \\u{d83c}\\udf7a"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''hello 🍺''' // contains the beer icon U+1F37 A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "valid surrogate pair - trailing code with braces"() {
        given:
        def input = '''"hello \\ud83c\\u{df7a}"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''hello 🍺''' // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "valid surrogate pair - leading and trailing code with braces"() {
        given:
        def input = '''"hello \\u{d83c}\\u{df7a}"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''hello 🍺''' // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "invalid surrogate pair - leading code with only \\ at end of string"() {
        given:
        def input = '''"hello \\u{d83c}\\"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - leading code with only \\u at end of string"() {
        given:
        def input = '''"hello \\u{d83c}\\u"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - trailing code without closing brace"() {
        given:
        def input = '''"hello \\u{d83c}\\u{df7a"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid trailing code without unicode escape 1"() {
        given:
        def input = '''"hello \\u{d83c}{df7a}"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid trailing code without unicode escape 2"() {
        given:
        def input = '''"hello \\u{d83c}df7a"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid leading code"() {
        given:
        def input = '''"hello d83c\\u{df7a}"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid leading value with braces"() {
        given:
        def input = '''"\\u{5B57}\\uDC00"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid surrogate pair - invalid trailing value with braces"() {
        given:
        def input = '''"\\uD83D\\u{DBFF}"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }

    def "invalid unicode code point - value is too high"() {
        given:
        def input = '''"\\u{fffffff}"'''

        when:
        StringValueParsing.parseSingleQuotedString(input)

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "Invalid unicode"
    }
}
