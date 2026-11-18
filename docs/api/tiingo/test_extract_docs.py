from extract_docs import (
    API_TOKEN_PLACEHOLDER,
    Expression,
    Node,
    build_tree,
    bundle_urls,
    component_fields,
    components,
    element_attributes,
    instruction_calls,
    interpolated_text,
    matching_bracket,
    parse_js_value,
    readable_expression,
    render_children,
    split_top_level,
    table_markdown,
    template_blocks,
    unescape_js_string,
)

SEARCH_COMPONENT = (
    "pe=(()=>{class e{constructor(e){this.canvasService=e,"
    'this.responseRows=[{name:"Ticker",jsonFieldName:"ticker",jsonFieldType:"string",description:"Ticker | id."}],'
    'this.requestRows=[{name:"Query",required:!0,jsonFieldName:"query",jsonFieldType:"string",description:"Text."}]}'
    'ngOnInit(){this.documentationService.setDocumentationHeader("5.1 Utilities - Search")}}'
    'return e.\\u0275cmp=s.Xpm({type:e,selectors:[["tiingo-search"]],decls:20,vars:2,'
    'consts:[[1,"documentation-breadcrumb-top-header"],["tableType","response",3,"tableRows"],'
    '["tableType","request",3,"tableRows"],["label","Python"],["label","Response"]],'
    "template:function(e,t){1&e&&("
    's.TgZ(0,"div",0),s._uU(1," 5.1 Utilities - Search "),s.qZA(),'
    's.TgZ(2,"h1"),s._uU(3,"Overview"),s.qZA(),'
    's.TgZ(4,"p"),s._uU(5,"Find "),s.TgZ(6,"b"),s._uU(7,"assets"),s.qZA(),s._uU(8," by name."),s.qZA(),'
    's.TgZ(9,"mat-tab",4),s._UZ(10,"tiingo-doc-table",1),s._UZ(11,"tiingo-doc-table",2),s.qZA(),'
    's.TgZ(12,"mat-tab",3),s.TgZ(13,"pre"),s._uU(14),s.qZA(),s.qZA(),'
    's.TgZ(15,"pre"),s._uU(16),s.qZA()'
    '),2&e&&(s.xp6(10),s.Q6J("tableRows",t.responseRows),s.xp6(1),s.Q6J("tableRows",t.requestRows),'
    's.xp6(3),s.hij("requests.get(\\"https://api.tiingo.com/x?token=",null==t.apiToken?null:t.apiToken.token,"\\")"),'
    's.xp6(2),s.HOy("[","{",\'"ticker":"AAPL"\',"}","]"))},styles:[""]}),e})()'
)


def test_unescape_js_string_handles_escapes_and_line_continuations() -> None:
    assert unescape_js_string(r"a\nb\'cé\x41\\") == "a\nb'céA\\"
    assert unescape_js_string("a\\\nb") == "ab"


def test_matching_bracket_skips_brackets_inside_strings() -> None:
    source = 'f("(",[1,")"],{a:"}"})'
    assert matching_bracket(source, 1) == len(source) - 1


def test_split_top_level_respects_nesting_and_strings() -> None:
    assert split_top_level('1,"a,b",[2,3],{k:4},f(5,6)') == ["1", '"a,b"', "[2,3]", "{k:4}", "f(5,6)"]


def test_parse_js_value_reads_literals() -> None:
    assert parse_js_value('{name:"x",required:!0,"q":[1,2.5,null,!1]}') == {
        "name": "x",
        "required": True,
        "q": [1, 2.5, None, False],
    }
    assert parse_js_value("t.apiToken") == Expression("t.apiToken")
    assert parse_js_value("'it\\'s'") == "it's"


def test_instruction_calls_yields_top_level_calls_only() -> None:
    calls = list(instruction_calls('s.TgZ(0,"div"),s._uU(1,"a(b)"),s.hij("x",s.get(1))'))
    assert [(call.name, call.arguments) for call in calls] == [
        ("TgZ", ["0", '"div"']),
        ("_uU", ["1", '"a(b)"']),
        ("hij", ['"x"', "s.get(1)"]),
    ]


def test_template_blocks_split_create_and_update() -> None:
    assert template_blocks('{1&e&&(s.TgZ(0,"p"),s.qZA()),2&e&&(s.xp6(1))}') == ('s.TgZ(0,"p"),s.qZA()', "s.xp6(1)")


def test_element_attributes_reads_attributes_and_classes() -> None:
    attributes, classes = element_attributes(["label", "Response", 1, "big", "bold", 3, "tableRows"])
    assert attributes == {"label": "Response"}
    assert classes == frozenset({"big", "bold"})


def test_interpolated_text_replaces_token_and_shortens_bindings() -> None:
    text = interpolated_text(['"a?token="', "null==t.apiToken?null:t.apiToken.token", '"&x="', "t.count"])
    assert text == f"a?token={API_TOKEN_PLACEHOLDER}&x={{count}}"
    assert interpolated_text(['["[","{","}"]']) == "[{}"


def test_readable_expression_unwraps_pipes() -> None:
    source = 'o.xi3(56,28,null==t.stats?null:t.stats.count,"1.0-0")'
    assert readable_expression(source) == "stats.count"


def test_component_fields_reads_constructor_literals() -> None:
    fields = component_fields('class e{constructor(){this.rows=[{a:"b"}],this.title="T",this.x=t}}')
    assert fields == {"rows": [{"a": "b"}], "title": "T"}


def test_build_tree_applies_table_and_text_bindings() -> None:
    root = build_tree(
        's.TgZ(0,"div"),s._UZ(1,"tiingo-doc-table"),s.TgZ(2,"pre"),s._uU(3),s.qZA(),s.qZA()',
        's.xp6(1),s.Q6J("tableRows",t.rows),s.xp6(2),s.Oqu("hello")',
        [],
        {"rows": [{"jsonFieldName": "a"}]},
    )
    div = root.children[0]
    assert div.children[0].table_rows == [{"jsonFieldName": "a"}]
    assert div.children[1].children[0].text == "hello"


def test_table_markdown_renders_request_rows() -> None:
    rows = [
        {"name": "Query", "required": True, "jsonFieldName": "query", "jsonFieldType": "string", "description": "a|b"}
    ]
    assert table_markdown(rows, "request").splitlines()[2] == "| `query` | Query | yes | string | a/b |"
    assert table_markdown([], "response") == ""


def test_render_children_groups_inline_text_into_paragraphs() -> None:
    paragraph = Node("p", children=[Node("#text", text="Hi "), Node("b", children=[Node("#text", text="you")])])
    assert render_children(Node("root", children=[paragraph])) == ["Hi **you**"]


def test_components_render_a_documentation_page() -> None:
    [component] = list(components(SEARCH_COMPONENT))
    blocks = render_children(component.template)

    assert component.selector == "tiingo-search"
    assert component.title == "5.1 Utilities - Search"
    assert blocks[0] == "## Overview"
    assert blocks[1] == "Find **assets** by name."
    assert blocks[2] == "**Response**"
    assert "| `ticker` | Ticker | string | Ticker / id. |" in blocks[3]
    assert "| `query` | Query | yes | string | Text. |" in blocks[4]
    assert blocks[5] == '```\n[{"ticker":"AAPL"}]\n```'
    assert len(blocks) == 6


def test_bundle_urls_combines_entry_scripts_and_lazy_chunks() -> None:
    page = '<script src="https://apimedia.tiingo.com/dist/main-es2015.abc.js"></script>'
    runtime = 'r.u=function(e){return e+"-es2015."+{common:"d1",docs_module:"f2"}[e]+".js"}'
    assert bundle_urls(page, runtime) == {
        "main": "https://apimedia.tiingo.com/dist/main-es2015.abc.js",
        "common": "https://apimedia.tiingo.com/dist/common-es2015.d1.js",
        "docs_module": "https://apimedia.tiingo.com/dist/docs_module-es2015.f2.js",
    }
