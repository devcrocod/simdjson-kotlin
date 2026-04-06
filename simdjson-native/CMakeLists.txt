cmake_minimum_required(VERSION 3.16)
project(simdjson_c LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(CMAKE_SYSTEM_NAME STREQUAL "iOS")
    set(CMAKE_OSX_DEPLOYMENT_TARGET "14.0" CACHE STRING "Minimum iOS deployment target")
endif()

include(FetchContent)
FetchContent_Declare(simdjson
    GIT_REPOSITORY https://github.com/simdjson/simdjson.git
    GIT_TAG        v4.6.1
    GIT_SHALLOW    ON
)
FetchContent_MakeAvailable(simdjson)
set_target_properties(simdjson PROPERTIES EXCLUDE_FROM_ALL TRUE)
set(SIMDJSON_SINGLEHEADER_DIR "${simdjson_SOURCE_DIR}/singleheader")

add_library(simdjson_c STATIC
    simdjson_c.cpp
    ${SIMDJSON_SINGLEHEADER_DIR}/simdjson.cpp
)

target_include_directories(simdjson_c
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PRIVATE ${SIMDJSON_SINGLEHEADER_DIR}
)

set_target_properties(simdjson_c PROPERTIES POSITION_INDEPENDENT_CODE ON)

install(TARGETS simdjson_c ARCHIVE DESTINATION lib)
install(FILES simdjson_c.h DESTINATION include)
