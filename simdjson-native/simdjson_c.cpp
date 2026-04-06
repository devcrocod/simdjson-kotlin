#include "simdjson_c.h"
#include "simdjson.h"

#include <new>
#include <string_view>

// ---------------------------------------------------------------------------
// Mode discriminator
// ---------------------------------------------------------------------------

enum simdjson_api_mode { MODE_ONDEMAND, MODE_DOM };

// ---------------------------------------------------------------------------
// Internal struct definitions
// ---------------------------------------------------------------------------

struct simdjson_parser_s {
    simdjson::ondemand::parser od_parser;
    simdjson::dom::parser dom_parser;
    simdjson::padded_string padded_buf;
    size_t max_depth;

    simdjson_parser_s(size_t capacity, size_t max_depth)
        : od_parser(capacity), dom_parser(capacity), max_depth(max_depth) {
        if (auto err = od_parser.allocate(capacity, max_depth)) {
            throw std::runtime_error("ondemand parser allocate failed");
        }
        if (auto err = dom_parser.allocate(capacity, max_depth)) {
            throw std::runtime_error("dom parser allocate failed");
        }
    }
};

struct simdjson_document_s {
    simdjson_api_mode mode;
    // On Demand — heap-allocated so its address stays stable
    simdjson::ondemand::document* od_doc;
    // DOM — lightweight tape reference
    simdjson::dom::element dom_elem;
    simdjson_parser_t parser;
};

struct simdjson_object_s {
    simdjson_api_mode mode;
    simdjson::ondemand::object od_obj;
    simdjson::dom::object dom_obj;
};

struct simdjson_array_s {
    simdjson_api_mode mode;
    simdjson::ondemand::array od_arr;
    simdjson::dom::array dom_arr;
};

struct simdjson_value_s {
    simdjson_api_mode mode;
    simdjson::ondemand::value od_val;
    simdjson::dom::element dom_elem;
};

struct simdjson_object_iterator_s {
    simdjson_api_mode mode;
    // On Demand
    simdjson::ondemand::object_iterator od_iter;
    simdjson::ondemand::object_iterator od_end;
    bool od_needs_advance = false;
    // DOM
    simdjson::dom::object::iterator dom_iter;
    simdjson::dom::object::iterator dom_end;
};

struct simdjson_array_iterator_s {
    simdjson_api_mode mode;
    // On Demand
    simdjson::ondemand::array_iterator od_iter;
    simdjson::ondemand::array_iterator od_end;
    bool od_needs_advance = false;
    // DOM
    simdjson::dom::array::iterator dom_iter;
    simdjson::dom::array::iterator dom_end;
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static simdjson_error_code translate_error(simdjson::error_code ec) {
    switch (ec) {
        case simdjson::SUCCESS:                    return SIMDJSON_OK;
        case simdjson::CAPACITY:                   return SIMDJSON_CAPACITY;
        case simdjson::MEMALLOC:                   return SIMDJSON_MEMALLOC;
        case simdjson::TAPE_ERROR:                 return SIMDJSON_TAPE_ERROR;
        case simdjson::DEPTH_ERROR:                return SIMDJSON_DEPTH_ERROR;
        case simdjson::STRING_ERROR:               return SIMDJSON_STRING_ERROR;
        case simdjson::NUMBER_ERROR:               return SIMDJSON_NUMBER_ERROR;
        case simdjson::UTF8_ERROR:                 return SIMDJSON_UTF8_ERROR;
        case simdjson::UNINITIALIZED:              return SIMDJSON_UNINITIALIZED;
        case simdjson::EMPTY:                      return SIMDJSON_EMPTY;
        case simdjson::INCORRECT_TYPE:             return SIMDJSON_INCORRECT_TYPE;
        case simdjson::INDEX_OUT_OF_BOUNDS:        return SIMDJSON_INDEX_OUT_OF_BOUNDS;
        case simdjson::NO_SUCH_FIELD:              return SIMDJSON_NO_SUCH_FIELD;
        case simdjson::OUT_OF_ORDER_ITERATION:     return SIMDJSON_OUT_OF_ORDER_ITERATION;
        case simdjson::SCALAR_DOCUMENT_AS_VALUE:   return SIMDJSON_SCALAR_DOCUMENT_AS_VALUE;
        default:                                   return SIMDJSON_UNKNOWN_ERROR;
    }
}

static simdjson_value_type translate_type_od(simdjson::ondemand::json_type t) {
    switch (t) {
        case simdjson::ondemand::json_type::object:  return SIMDJSON_TYPE_OBJECT;
        case simdjson::ondemand::json_type::array:   return SIMDJSON_TYPE_ARRAY;
        case simdjson::ondemand::json_type::string:  return SIMDJSON_TYPE_STRING;
        case simdjson::ondemand::json_type::number:  return SIMDJSON_TYPE_NUMBER;
        case simdjson::ondemand::json_type::boolean: return SIMDJSON_TYPE_BOOLEAN;
        case simdjson::ondemand::json_type::null:    return SIMDJSON_TYPE_NULL;
        default:                                     return SIMDJSON_TYPE_NULL;
    }
}

static simdjson_value_type translate_type_dom(simdjson::dom::element_type t) {
    switch (t) {
        case simdjson::dom::element_type::OBJECT:     return SIMDJSON_TYPE_OBJECT;
        case simdjson::dom::element_type::ARRAY:      return SIMDJSON_TYPE_ARRAY;
        case simdjson::dom::element_type::STRING:     return SIMDJSON_TYPE_STRING;
        case simdjson::dom::element_type::INT64:
        case simdjson::dom::element_type::UINT64:
        case simdjson::dom::element_type::DOUBLE:     return SIMDJSON_TYPE_NUMBER;
        case simdjson::dom::element_type::BOOL:       return SIMDJSON_TYPE_BOOLEAN;
        case simdjson::dom::element_type::NULL_VALUE: return SIMDJSON_TYPE_NULL;
        default:                                      return SIMDJSON_TYPE_NULL;
    }
}

// ---------------------------------------------------------------------------
// Parser lifecycle
// ---------------------------------------------------------------------------

simdjson_parser_t simdjson_parser_create(size_t capacity, size_t max_depth) {
    try {
        return new simdjson_parser_s(capacity, max_depth);
    } catch (...) {
        return nullptr;
    }
}

void simdjson_parser_destroy(simdjson_parser_t parser) {
    delete parser;
}

// ---------------------------------------------------------------------------
// On Demand: iterate
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_iterate(
    simdjson_parser_t parser,
    const uint8_t* data, size_t length,
    simdjson_document_t* out_doc,
    size_t* error_offset)
{
    if (!parser || !data || !out_doc) return SIMDJSON_UNKNOWN_ERROR;

    if (error_offset) *error_offset = SIZE_MAX;

    // Copy into padded buffer (On Demand requires buffer to outlive iteration)
    parser->padded_buf = simdjson::padded_string(
        reinterpret_cast<const char*>(data), length);

    auto result = parser->od_parser.iterate(parser->padded_buf);
    if (result.error()) return translate_error(result.error());

    auto* doc = new (std::nothrow) simdjson_document_s{};
    if (!doc) return SIMDJSON_MEMALLOC;

    doc->mode = MODE_ONDEMAND;
    doc->od_doc = new (std::nothrow) simdjson::ondemand::document(
        std::move(result.value_unsafe()));
    if (!doc->od_doc) { delete doc; return SIMDJSON_MEMALLOC; }
    doc->parser = parser;
    *out_doc = doc;
    return SIMDJSON_OK;
}

// ---------------------------------------------------------------------------
// DOM: parse
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_dom_parse(
    simdjson_parser_t parser,
    const uint8_t* data, size_t length,
    simdjson_document_t* out_doc,
    size_t* error_offset)
{
    if (!parser || !data || !out_doc) return SIMDJSON_UNKNOWN_ERROR;

    if (error_offset) *error_offset = SIZE_MAX;

    // Copy into padded buffer explicitly — the caller (JNI/cinterop) passes raw
    // byte-array pointers that have no SIMDJSON_PADDING guarantee.  We reuse the
    // parser's padded_buf so the allocation is amortised across calls.
    parser->padded_buf = simdjson::padded_string(
        reinterpret_cast<const char*>(data), length);

    auto result = parser->dom_parser.parse(parser->padded_buf);
    if (result.error()) return translate_error(result.error());

    auto* doc = new (std::nothrow) simdjson_document_s{};
    if (!doc) return SIMDJSON_MEMALLOC;

    doc->mode = MODE_DOM;
    doc->od_doc = nullptr;
    doc->dom_elem = result.value_unsafe();
    doc->parser = parser;
    *out_doc = doc;
    return SIMDJSON_OK;
}

// ---------------------------------------------------------------------------
// Document destroy
// ---------------------------------------------------------------------------

void simdjson_document_destroy(simdjson_document_t doc) {
    if (!doc) return;
    delete doc->od_doc;
    delete doc;
}

// ---------------------------------------------------------------------------
// Document accessors
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_doc_get_object(simdjson_document_t doc, simdjson_object_t* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    auto* obj = new (std::nothrow) simdjson_object_s{};
    if (!obj) return SIMDJSON_MEMALLOC;
    obj->mode = doc->mode;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_object();
        if (result.error()) { delete obj; return translate_error(result.error()); }
        obj->od_obj = result.value_unsafe();
    } else {
        auto result = doc->dom_elem.get_object();
        if (result.error()) { delete obj; return translate_error(result.error()); }
        obj->dom_obj = result.value_unsafe();
    }
    *out = obj;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_array(simdjson_document_t doc, simdjson_array_t* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    auto* arr = new (std::nothrow) simdjson_array_s{};
    if (!arr) return SIMDJSON_MEMALLOC;
    arr->mode = doc->mode;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_array();
        if (result.error()) { delete arr; return translate_error(result.error()); }
        arr->od_arr = result.value_unsafe();
    } else {
        auto result = doc->dom_elem.get_array();
        if (result.error()) { delete arr; return translate_error(result.error()); }
        arr->dom_arr = result.value_unsafe();
    }
    *out = arr;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_string(simdjson_document_t doc, const char** out, size_t* out_len) {
    if (!doc || !out || !out_len) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_string();
        if (result.error()) return translate_error(result.error());
        std::string_view sv = result.value_unsafe();
        *out = sv.data();
        *out_len = sv.size();
    } else {
        auto result = doc->dom_elem.get_string();
        if (result.error()) return translate_error(result.error());
        std::string_view sv = result.value_unsafe();
        *out = sv.data();
        *out_len = sv.size();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_int64(simdjson_document_t doc, int64_t* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_int64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = doc->dom_elem.get_int64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_uint64(simdjson_document_t doc, uint64_t* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_uint64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = doc->dom_elem.get_uint64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_double(simdjson_document_t doc, double* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_double();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = doc->dom_elem.get_double();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_bool(simdjson_document_t doc, bool* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->get_bool();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = doc->dom_elem.get_bool();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_is_null(simdjson_document_t doc, bool* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->is_null();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        *out = doc->dom_elem.is_null();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_doc_get_type(simdjson_document_t doc, simdjson_value_type* out) {
    if (!doc || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (doc->mode == MODE_ONDEMAND) {
        auto result = doc->od_doc->type();
        if (result.error()) return translate_error(result.error());
        *out = translate_type_od(result.value_unsafe());
    } else {
        *out = translate_type_dom(doc->dom_elem.type());
    }
    return SIMDJSON_OK;
}

// ---------------------------------------------------------------------------
// Object accessors
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_object_find_field(
    simdjson_object_t obj, const char* key, size_t key_len,
    simdjson_value_t* out)
{
    if (!obj || !key || !out) return SIMDJSON_UNKNOWN_ERROR;
    std::string_view k(key, key_len);

    auto* val = new (std::nothrow) simdjson_value_s{};
    if (!val) return SIMDJSON_MEMALLOC;
    val->mode = obj->mode;

    if (obj->mode == MODE_ONDEMAND) {
        auto result = obj->od_obj.find_field(k);
        if (result.error()) { delete val; return translate_error(result.error()); }
        val->od_val = result.value_unsafe();
    } else {
        // DOM has no order-sensitive lookup; use operator[]
        auto result = obj->dom_obj[k];
        if (result.error()) { delete val; return translate_error(result.error()); }
        val->dom_elem = result.value_unsafe();
    }
    *out = val;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_object_find_field_unordered(
    simdjson_object_t obj, const char* key, size_t key_len,
    simdjson_value_t* out)
{
    if (!obj || !key || !out) return SIMDJSON_UNKNOWN_ERROR;
    std::string_view k(key, key_len);

    auto* val = new (std::nothrow) simdjson_value_s{};
    if (!val) return SIMDJSON_MEMALLOC;
    val->mode = obj->mode;

    if (obj->mode == MODE_ONDEMAND) {
        auto result = obj->od_obj.find_field_unordered(k);
        if (result.error()) { delete val; return translate_error(result.error()); }
        val->od_val = result.value_unsafe();
    } else {
        auto result = obj->dom_obj[k];
        if (result.error()) { delete val; return translate_error(result.error()); }
        val->dom_elem = result.value_unsafe();
    }
    *out = val;
    return SIMDJSON_OK;
}

// ---------------------------------------------------------------------------
// Object iterator
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_object_iterator_create(
    simdjson_object_t obj, simdjson_object_iterator_t* out)
{
    if (!obj || !out) return SIMDJSON_UNKNOWN_ERROR;

    auto* iter = new (std::nothrow) simdjson_object_iterator_s{};
    if (!iter) return SIMDJSON_MEMALLOC;
    iter->mode = obj->mode;

    if (obj->mode == MODE_ONDEMAND) {
        auto begin_r = obj->od_obj.begin();
        if (begin_r.error()) { delete iter; return translate_error(begin_r.error()); }
        auto end_r = obj->od_obj.end();
        if (end_r.error()) { delete iter; return translate_error(end_r.error()); }
        iter->od_iter = begin_r.value_unsafe();
        iter->od_end = end_r.value_unsafe();
    } else {
        iter->dom_iter = obj->dom_obj.begin();
        iter->dom_end = obj->dom_obj.end();
    }
    *out = iter;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_object_iterator_next(
    simdjson_object_iterator_t iter,
    bool* has_next, const char** key, size_t* key_len,
    simdjson_value_t* value)
{
    if (!iter || !has_next) return SIMDJSON_UNKNOWN_ERROR;

    if (iter->mode == MODE_ONDEMAND) {
        // Deferred advance: advance past the previous field before getting the next one.
        if (iter->od_needs_advance) {
            ++iter->od_iter;
            iter->od_needs_advance = false;
        }
        if (iter->od_iter == iter->od_end) {
            *has_next = false;
            return SIMDJSON_OK;
        }
        auto field = *iter->od_iter;
        // Extract key
        auto key_result = field.unescaped_key();
        if (key_result.error()) return translate_error(key_result.error());
        std::string_view k = key_result.value_unsafe();
        if (key) *key = k.data();
        if (key_len) *key_len = k.size();
        // Extract value
        if (value) {
            auto* val = new (std::nothrow) simdjson_value_s{};
            if (!val) return SIMDJSON_MEMALLOC;
            val->mode = MODE_ONDEMAND;
            val->od_val = field.value();
            *value = val;
        }
        iter->od_needs_advance = true;
        *has_next = true;
    } else {
        if (iter->dom_iter == iter->dom_end) {
            *has_next = false;
            return SIMDJSON_OK;
        }
        auto kv = *iter->dom_iter;
        if (key) *key = kv.key.data();
        if (key_len) *key_len = kv.key.size();
        if (value) {
            auto* val = new (std::nothrow) simdjson_value_s{};
            if (!val) return SIMDJSON_MEMALLOC;
            val->mode = MODE_DOM;
            val->dom_elem = kv.value;
            *value = val;
        }
        ++iter->dom_iter;
        *has_next = true;
    }
    return SIMDJSON_OK;
}

void simdjson_object_iterator_destroy(simdjson_object_iterator_t iter) {
    delete iter;
}

void simdjson_object_destroy(simdjson_object_t obj) {
    delete obj;
}

// ---------------------------------------------------------------------------
// Array iterator
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_array_iterator_create(
    simdjson_array_t arr, simdjson_array_iterator_t* out)
{
    if (!arr || !out) return SIMDJSON_UNKNOWN_ERROR;

    auto* iter = new (std::nothrow) simdjson_array_iterator_s{};
    if (!iter) return SIMDJSON_MEMALLOC;
    iter->mode = arr->mode;

    if (arr->mode == MODE_ONDEMAND) {
        auto begin_r = arr->od_arr.begin();
        if (begin_r.error()) { delete iter; return translate_error(begin_r.error()); }
        auto end_r = arr->od_arr.end();
        if (end_r.error()) { delete iter; return translate_error(end_r.error()); }
        iter->od_iter = begin_r.value_unsafe();
        iter->od_end = end_r.value_unsafe();
    } else {
        iter->dom_iter = arr->dom_arr.begin();
        iter->dom_end = arr->dom_arr.end();
    }
    *out = iter;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_array_iterator_next(
    simdjson_array_iterator_t iter,
    bool* has_next, simdjson_value_t* value)
{
    if (!iter || !has_next) return SIMDJSON_UNKNOWN_ERROR;

    if (iter->mode == MODE_ONDEMAND) {
        // Deferred advance: advance past the previous element before getting the next one.
        // This ensures the previous value is consumed before the iterator moves.
        if (iter->od_needs_advance) {
            ++iter->od_iter;
            iter->od_needs_advance = false;
        }
        if (iter->od_iter == iter->od_end) {
            *has_next = false;
            return SIMDJSON_OK;
        }
        if (value) {
            auto* val = new (std::nothrow) simdjson_value_s{};
            if (!val) return SIMDJSON_MEMALLOC;
            val->mode = MODE_ONDEMAND;
            val->od_val = *iter->od_iter;
            *value = val;
        }
        iter->od_needs_advance = true;
        *has_next = true;
    } else {
        if (iter->dom_iter == iter->dom_end) {
            *has_next = false;
            return SIMDJSON_OK;
        }
        if (value) {
            auto* val = new (std::nothrow) simdjson_value_s{};
            if (!val) return SIMDJSON_MEMALLOC;
            val->mode = MODE_DOM;
            val->dom_elem = *iter->dom_iter;
            *value = val;
        }
        ++iter->dom_iter;
        *has_next = true;
    }
    return SIMDJSON_OK;
}

void simdjson_array_iterator_destroy(simdjson_array_iterator_t iter) {
    delete iter;
}

void simdjson_array_destroy(simdjson_array_t arr) {
    delete arr;
}

// ---------------------------------------------------------------------------
// Value accessors
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_value_get_object(simdjson_value_t val, simdjson_object_t* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    auto* obj = new (std::nothrow) simdjson_object_s{};
    if (!obj) return SIMDJSON_MEMALLOC;
    obj->mode = val->mode;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_object();
        if (result.error()) { delete obj; return translate_error(result.error()); }
        obj->od_obj = result.value_unsafe();
    } else {
        auto result = val->dom_elem.get_object();
        if (result.error()) { delete obj; return translate_error(result.error()); }
        obj->dom_obj = result.value_unsafe();
    }
    *out = obj;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_array(simdjson_value_t val, simdjson_array_t* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    auto* arr = new (std::nothrow) simdjson_array_s{};
    if (!arr) return SIMDJSON_MEMALLOC;
    arr->mode = val->mode;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_array();
        if (result.error()) { delete arr; return translate_error(result.error()); }
        arr->od_arr = result.value_unsafe();
    } else {
        auto result = val->dom_elem.get_array();
        if (result.error()) { delete arr; return translate_error(result.error()); }
        arr->dom_arr = result.value_unsafe();
    }
    *out = arr;
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_string(simdjson_value_t val, const char** out, size_t* out_len) {
    if (!val || !out || !out_len) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_string();
        if (result.error()) return translate_error(result.error());
        std::string_view sv = result.value_unsafe();
        *out = sv.data();
        *out_len = sv.size();
    } else {
        auto result = val->dom_elem.get_string();
        if (result.error()) return translate_error(result.error());
        std::string_view sv = result.value_unsafe();
        *out = sv.data();
        *out_len = sv.size();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_int64(simdjson_value_t val, int64_t* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_int64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = val->dom_elem.get_int64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_uint64(simdjson_value_t val, uint64_t* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_uint64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = val->dom_elem.get_uint64();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_double(simdjson_value_t val, double* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_double();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = val->dom_elem.get_double();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_bool(simdjson_value_t val, bool* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.get_bool();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        auto result = val->dom_elem.get_bool();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_is_null(simdjson_value_t val, bool* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.is_null();
        if (result.error()) return translate_error(result.error());
        *out = result.value_unsafe();
    } else {
        *out = val->dom_elem.is_null();
    }
    return SIMDJSON_OK;
}

simdjson_error_code simdjson_value_get_type(simdjson_value_t val, simdjson_value_type* out) {
    if (!val || !out) return SIMDJSON_UNKNOWN_ERROR;

    if (val->mode == MODE_ONDEMAND) {
        auto result = val->od_val.type();
        if (result.error()) return translate_error(result.error());
        *out = translate_type_od(result.value_unsafe());
    } else {
        *out = translate_type_dom(val->dom_elem.type());
    }
    return SIMDJSON_OK;
}

void simdjson_value_destroy(simdjson_value_t val) {
    delete val;
}

// ---------------------------------------------------------------------------
// Error message
// ---------------------------------------------------------------------------

const char* simdjson_error_message(simdjson_error_code error) {
    switch (error) {
        case SIMDJSON_OK:                        return "No error";
        case SIMDJSON_CAPACITY:                  return "This parser can't support a document that big";
        case SIMDJSON_MEMALLOC:                  return "Error allocating memory, we're most likely out of memory";
        case SIMDJSON_TAPE_ERROR:                return "The JSON document has an improper structure: missing or superfluous commas, braces, missing keys, etc.";
        case SIMDJSON_DEPTH_ERROR:               return "The JSON document was too deep (too many nested objects and arrays)";
        case SIMDJSON_STRING_ERROR:              return "Problem while parsing a string";
        case SIMDJSON_NUMBER_ERROR:              return "Problem while parsing a number";
        case SIMDJSON_UTF8_ERROR:                return "The input is not valid UTF-8";
        case SIMDJSON_UNINITIALIZED:             return "Uninitialized";
        case SIMDJSON_EMPTY:                     return "Empty: no JSON found";
        case SIMDJSON_INCORRECT_TYPE:            return "The JSON element does not have the requested type";
        case SIMDJSON_INDEX_OUT_OF_BOUNDS:       return "Array index out of bounds";
        case SIMDJSON_NO_SUCH_FIELD:             return "Field not found in object";
        case SIMDJSON_OUT_OF_ORDER_ITERATION:    return "Objects and arrays can only be iterated when they are first encountered";
        case SIMDJSON_SCALAR_DOCUMENT_AS_VALUE:  return "A scalar document is treated as a value";
        case SIMDJSON_UNKNOWN_ERROR:             return "Unknown error";
        default:                                 return "Unknown error";
    }
}

// ---------------------------------------------------------------------------
// Stage 1: structural indexing
// ---------------------------------------------------------------------------

simdjson_error_code simdjson_stage1(
    simdjson_parser_t parser,
    const uint8_t* data, size_t length,
    const uint32_t** out_indices,
    size_t* out_count)
{
    if (!parser || !data || !out_indices || !out_count) return SIMDJSON_UNKNOWN_ERROR;

    // Copy into padded buffer (stage1 requires SIMDJSON_PADDING)
    parser->padded_buf = simdjson::padded_string(
        reinterpret_cast<const char*>(data), length);

    // Access the internal dom_parser_implementation for stage1
    auto& impl = *parser->dom_parser.implementation;
    auto err = impl.stage1(
        reinterpret_cast<const uint8_t*>(parser->padded_buf.data()),
        length,
        simdjson::stage1_mode::regular);
    if (err) return translate_error(err);

    *out_indices = impl.structural_indexes.get();
    *out_count = impl.n_structural_indexes;
    return SIMDJSON_OK;
}
