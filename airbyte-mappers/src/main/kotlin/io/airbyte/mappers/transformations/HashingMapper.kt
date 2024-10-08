package io.airbyte.mappers.transformations

import io.airbyte.config.ConfiguredMapper
import io.airbyte.config.Field
import io.airbyte.config.FieldType
import io.airbyte.config.MapperOperationName
import io.airbyte.config.MapperSpecification
import io.airbyte.config.MapperSpecificationFieldEnum
import io.airbyte.config.MapperSpecificationFieldString
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.util.Base64

@Singleton
@Named("HashingMapper")
class HashingMapper : Mapper {
  companion object {
    // Needed configuration keys
    const val TARGET_FIELD_CONFIG_KEY = "targetField"
    const val METHOD_CONFIG_KEY = "method"
    const val FIELD_NAME_SUFFIX_CONFIG_KEY = "fieldNameSuffix"

    // Supported hashing methods
    const val MD2 = "MD2"
    const val MD5 = "MD5"
    const val SHA1 = "SHA-1"
    const val SHA224 = "SHA-224"
    const val SHA256 = "SHA-256"
    const val SHA384 = "SHA-384"
    const val SHA512 = "SHA-512"

    val supportedMethods = listOf(MD2, MD5, SHA1, SHA224, SHA256, SHA384, SHA512)
  }

  override val name: String
    get() = MapperOperationName.HASHING

  override fun spec(): MapperSpecification {
    return MapperSpecification(
      name = name,
      documentationUrl = "",
      config =
        mapOf(
          TARGET_FIELD_CONFIG_KEY to
            MapperSpecificationFieldString(
              title = "Field",
              description = "The field to hash.",
            ),
          METHOD_CONFIG_KEY to
            MapperSpecificationFieldEnum(
              title = "Hashing method",
              description = "The hashing algorithm to use.",
              enum = supportedMethods,
              default = SHA256,
              examples = listOf(SHA256),
            ),
          FIELD_NAME_SUFFIX_CONFIG_KEY to
            MapperSpecificationFieldString(
              title = "Field name suffix",
              description = "The suffix to append to the field name after hashing.",
              default = "_hashed",
            ),
        ),
    )
  }

  override fun schema(
    config: ConfiguredMapper,
    streamFields: List<Field>,
  ): List<Field> {
    val (targetField, _, fieldNameSuffix) = getConfigValues(config.config)
    val resultField = "$targetField$fieldNameSuffix"
    var fieldFound = false

    val result: List<Field> =
      streamFields.map {
        if (it.name == resultField) {
          throw IllegalStateException("Field $resultField already exists in stream fields")
        }
        if (it.name == targetField) {
          fieldFound = true
          it.copy(
            name = "${it.name}$fieldNameSuffix",
            type = FieldType.STRING,
          )
        } else {
          it
        }
      }

    if (fieldFound.not()) {
      throw IllegalStateException("Field $targetField not found in stream fields")
    }

    return result
  }

  override fun map(
    config: ConfiguredMapper,
    record: Record,
  ) {
    val (targetField, method, fieldNameSuffix) = getConfigValues(config.config)

    if (record.data.hasNonNull(targetField)) {
      val data = record.data.get(targetField).asText().toByteArray()

      val hashedAndEncodeValue: String = hashAndEncodeData(method, data)
      record.data.put(targetField + fieldNameSuffix, hashedAndEncodeValue)
      record.data.remove(targetField)
    }
  }

  internal fun hashAndEncodeData(
    method: String,
    data: ByteArray,
  ): String {
    if (supportedMethods.contains(method).not()) {
      throw IllegalArgumentException("Unsupported hashing method: $method")
    }

    val hashedValue = MessageDigest.getInstance(method).digest(data)

    return Base64.getEncoder().encodeToString(hashedValue)
  }

  data class HashingConfig(
    val targetField: String,
    val method: String,
    val fieldNameSuffix: String,
  )

  private fun getConfigValues(config: Map<String, String>): HashingConfig {
    return HashingConfig(
      config[TARGET_FIELD_CONFIG_KEY] ?: "",
      config[METHOD_CONFIG_KEY] ?: "",
      config[FIELD_NAME_SUFFIX_CONFIG_KEY] ?: "_hashed",
    )
  }
}
