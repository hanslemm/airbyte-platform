/*
 * Copyright (c) 2020-2024 Airbyte, Inc., all rights reserved.
 */
@file:Suppress("ktlint:standard:package-name")

package io.airbyte.connector_builder.requester.assist

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.airbyte.connector_builder.exceptions.AssistProxyException
import io.airbyte.connector_builder.exceptions.ConnectorBuilderException
import java.io.IOException
import java.io.InputStreamReader

class AssistProxy(private val proxyConfig: AssistConfiguration) {
  fun post(
    path: String,
    jsonBody: JsonNode?,
  ): JsonNode {
    val connection = proxyConfig.getConnection(path)
    connection.apply {
      requestMethod = "POST"
      setRequestProperty("Content-Type", "application/json")
      doOutput = true
    }

    connection.outputStream.use { outputStream ->
      objectMapper.writeValue(outputStream, jsonBody)
    }
    val responseCode: Int
    val jsonResponse: JsonNode

    try {
      responseCode = connection.responseCode
      val inputStream =
        if (responseCode in 200..299) {
          connection.inputStream
        } else {
          connection.errorStream
        }

      jsonResponse =
        inputStream.use { inputStream ->
          InputStreamReader(inputStream, "utf-8").use { reader ->
            reader.readText().let {
              objectMapper.readTree(it)
            }
          }
        }
    } catch (e: IOException) {
      throw ConnectorBuilderException("AI Assist processing error", e)
    } finally {
      connection.disconnect()
    }

    if (responseCode !in 200..299) {
      throw AssistProxyException(responseCode, jsonResponse)
    }

    return jsonResponse
  }

  companion object {
    private val objectMapper = ObjectMapper()
  }
}
