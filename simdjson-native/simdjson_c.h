#ifndef SIMDJSON_C_H
#define SIMDJSON_C_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque handles */
typedef struct simdjson_parser_s* simdjson_parser_t;
typedef struct simdjson_document_s* simdjson_document_t;
typedef struct simdjson_value_s* simdjson_value_t;
typedef struct simdjson_object_s* simdjson_object_t;
typedef struct simdjson_array_s* simdjson_array_t;
typedef struct simdjson_object_iterator_s* simdjson_object_iterator_t;
typedef struct simdjson_array_iterator_s* simdjson_array_iterator_t;

/* Error codes */
typedef enum {
    SIMDJSON_OK = 0,
    SIMDJSON_CAPACITY,
    SIMDJSON_MEMALLOC,
    SIMDJSON_TAPE_ERROR,
    SIMDJSON_DEPTH_ERROR,
    SIMDJSON_STRING_ERROR,
    SIMDJSON_NUMBER_ERROR,
    SIMDJSON_UTF8_ERROR,
    SIMDJSON_UNINITIALIZED,
    SIMDJSON_EMPTY,
    SIMDJSON_INCORRECT_TYPE,
    SIMDJSON_INDEX_OUT_OF_BOUNDS,
    SIMDJSON_NO_SUCH_FIELD,
    SIMDJSON_OUT_OF_ORDER_ITERATION,
    SIMDJSON_SCALAR_DOCUMENT_AS_VALUE,
    SIMDJSON_UNKNOWN_ERROR
} simdjson_error_code;

/* Value types */
typedef enum {
    SIMDJSON_TYPE_OBJECT = 1,
    SIMDJSON_TYPE_ARRAY,
    SIMDJSON_TYPE_STRING,
    SIMDJSON_TYPE_NUMBER,
    SIMDJSON_TYPE_BOOLEAN,
    SIMDJSON_TYPE_NULL
} simdjson_value_type;

/* Parser lifecycle */
simdjson_parser_t simdjson_parser_create(size_t capacity, size_t max_depth);
void simdjson_parser_destroy(simdjson_parser_t parser);

/* --- On Demand API --- */

/* Parse / iterate */
simdjson_error_code simdjson_iterate(
    simdjson_parser_t parser,
    const uint8_t* data, size_t length,
    simdjson_document_t* out_doc,
    size_t* error_offset
);
void simdjson_document_destroy(simdjson_document_t doc);

/* Document access */
simdjson_error_code simdjson_doc_get_object(simdjson_document_t doc, simdjson_object_t* out);
simdjson_error_code simdjson_doc_get_array(simdjson_document_t doc, simdjson_array_t* out);
simdjson_error_code simdjson_doc_get_string(simdjson_document_t doc, const char** out, size_t* out_len);
simdjson_error_code simdjson_doc_get_int64(simdjson_document_t doc, int64_t* out);
simdjson_error_code simdjson_doc_get_uint64(simdjson_document_t doc, uint64_t* out);
simdjson_error_code simdjson_doc_get_double(simdjson_document_t doc, double* out);
simdjson_error_code simdjson_doc_get_bool(simdjson_document_t doc, bool* out);
simdjson_error_code simdjson_doc_is_null(simdjson_document_t doc, bool* out);
simdjson_error_code simdjson_doc_get_type(simdjson_document_t doc, simdjson_value_type* out);

/* Object access */
simdjson_error_code simdjson_object_find_field(simdjson_object_t obj, const char* key, size_t key_len, simdjson_value_t* out);
simdjson_error_code simdjson_object_find_field_unordered(simdjson_object_t obj, const char* key, size_t key_len, simdjson_value_t* out);
simdjson_error_code simdjson_object_iterator_create(simdjson_object_t obj, simdjson_object_iterator_t* out);
simdjson_error_code simdjson_object_iterator_next(simdjson_object_iterator_t iter, bool* has_next, const char** key, size_t* key_len, simdjson_value_t* value);
void simdjson_object_iterator_destroy(simdjson_object_iterator_t iter);
void simdjson_object_destroy(simdjson_object_t obj);

/* Array access */
simdjson_error_code simdjson_array_iterator_create(simdjson_array_t arr, simdjson_array_iterator_t* out);
simdjson_error_code simdjson_array_iterator_next(simdjson_array_iterator_t iter, bool* has_next, simdjson_value_t* value);
void simdjson_array_iterator_destroy(simdjson_array_iterator_t iter);
void simdjson_array_destroy(simdjson_array_t arr);

/* Value access */
simdjson_error_code simdjson_value_get_object(simdjson_value_t val, simdjson_object_t* out);
simdjson_error_code simdjson_value_get_array(simdjson_value_t val, simdjson_array_t* out);
simdjson_error_code simdjson_value_get_string(simdjson_value_t val, const char** out, size_t* out_len);
simdjson_error_code simdjson_value_get_int64(simdjson_value_t val, int64_t* out);
simdjson_error_code simdjson_value_get_uint64(simdjson_value_t val, uint64_t* out);
simdjson_error_code simdjson_value_get_double(simdjson_value_t val, double* out);
simdjson_error_code simdjson_value_get_bool(simdjson_value_t val, bool* out);
simdjson_error_code simdjson_value_is_null(simdjson_value_t val, bool* out);
simdjson_error_code simdjson_value_get_type(simdjson_value_t val, simdjson_value_type* out);
void simdjson_value_destroy(simdjson_value_t val);

/* --- DOM API --- */

simdjson_error_code simdjson_dom_parse(
    simdjson_parser_t parser,
    const uint8_t* data, size_t length,
    simdjson_document_t* out_doc,
    size_t* error_offset
);

/* Error utilities */
const char* simdjson_error_message(simdjson_error_code error);

/* Stage 1: structural indexing */
simdjson_error_code simdjson_stage1(
    simdjson_parser_t parser,
    const uint8_t* data, size_t length,
    const uint32_t** out_indices,
    size_t* out_count
);

#ifdef __cplusplus
}
#endif

#endif /* SIMDJSON_C_H */
