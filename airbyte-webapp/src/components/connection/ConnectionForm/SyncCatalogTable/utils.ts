import { Row } from "@tanstack/react-table";
import isEqual from "lodash/isEqual";

import {
  AirbyteStreamAndConfiguration,
  AirbyteStreamConfiguration,
  SelectedFieldInfo,
} from "core/api/types/AirbyteClient";
import { Path, SyncSchemaField, SyncSchemaFieldObject, traverseSchemaToField } from "core/domain/catalog";

import { SyncCatalogUIModel } from "./SyncCatalogTable";
import { StatusToDisplay } from "../../syncCatalog/StreamsConfigTable/useStreamsConfigTableRowProps";
import { compareObjectsByFields, flattenSyncSchemaFields, getFieldPathDisplayName } from "../../syncCatalog/utils";
import { FormConnectionFormValues, SyncStreamFieldWithId } from "../formConfig";
import { isSameSyncStream } from "../utils";

// Streams
/**
 * Group streams by namespace
 * @param streams Array of stream nodes
 */
const groupStreamsByNamespace = (streams: SyncStreamFieldWithId[]) => {
  return streams.reduce((acc: Record<string, SyncStreamFieldWithId[]>, stream) => {
    const namespace = stream.stream?.namespace || ""; // Use empty string if namespace is undefined
    if (!acc[namespace]) {
      acc[namespace] = [];
    }
    acc[namespace].push(stream);
    return acc;
  }, {});
};

/**
 * Prepare all levels of rows: namespace => stream => fields => nestedFields
 * @param streams
 * @param initialStreams
 * @param prefix
 */
export const getSyncCatalogRows = (
  streams: SyncStreamFieldWithId[],
  initialStreams: FormConnectionFormValues["syncCatalog"]["streams"],
  prefix?: string
) => {
  const namespaceGroups = groupStreamsByNamespace(streams);

  return Object.entries(namespaceGroups).map(([namespace, groupedStreams]) => ({
    rowType: "namespace" as const,
    name: namespace,
    subRows: groupedStreams.map((streamNode) => {
      const traversedFields = traverseSchemaToField(streamNode.stream?.jsonSchema, streamNode.stream?.name);

      const initialStreamNode = initialStreams.find((item) =>
        isSameSyncStream(item, streamNode.stream?.name, streamNode.stream?.namespace)
      );

      return {
        rowType: "stream" as const,
        streamNode,
        initialStreamNode,
        name: `${prefix ? prefix : ""}${streamNode.stream?.name || ""}`,
        namespace: streamNode.stream?.namespace || "",
        isEnabled: streamNode.config?.selected,
        traversedFields, // we need all traversed fields for updating field
        subRows: traversedFields.map((rowField) => ({
          rowType: "field" as const,
          streamNode,
          initialStreamNode,
          name: getFieldPathDisplayName(rowField.path),
          field: rowField,
          traversedFields,
          subRows:
            rowField?.fields &&
            flattenSyncSchemaFields(rowField?.fields)?.map((nestedField) => ({
              rowType: "nestedField" as const,
              streamNode,
              initialStreamNode,
              name: getFieldPathDisplayName(nestedField.path),
              field: nestedField,
              traversedFields,
            })),
        })),
      };
    }),
  }));
};

export const isNamespaceRow = (row: Row<SyncCatalogUIModel>) => row.depth === 0 && row.original.rowType === "namespace";
export const isStreamRow = (row: Row<SyncCatalogUIModel>) => row.depth === 1 && row.original.rowType === "stream";

// Stream  Fields
/*
 * Check is stream field is selected(enabled) for sync
 */
export const checkIsFieldSelected = (field: SyncSchemaField, config: AirbyteStreamConfiguration): boolean => {
  // If the stream is disabled, effectively each field is unselected
  if (!config?.selected) {
    return false;
  }

  // All fields are implicitly selected if field selection is disabled
  if (!config?.fieldSelectionEnabled) {
    return true;
  }

  // path[0] is the top-level field name for all nested fields
  return !!config?.selectedFields?.find((f) => isEqual(f.fieldPath, [field.path[0]]));
};

export const pathDisplayName = (path: Path): string => path.join(".");

/**
 * Get change status  for stream: added, removed, changed, unchanged, disabled
 * @param initialStreamNode
 * @param streamNode
 */
export const getStreamChangeStatus = (
  initialStreamNode: AirbyteStreamAndConfiguration,
  streamNode: SyncStreamFieldWithId
): StatusToDisplay => {
  const isStreamEnabled = streamNode.config?.selected;
  const streamStatusChanged = initialStreamNode?.config?.selected !== streamNode.config?.selected;

  const streamChanged = !compareObjectsByFields<AirbyteStreamConfiguration>(
    initialStreamNode?.config,
    streamNode.config,
    ["syncMode", "destinationSyncMode", "cursorField", "primaryKey", "selectedFields", "fieldSelectionEnabled"]
  );

  if (!isStreamEnabled && !streamStatusChanged) {
    return "disabled";
  } else if (streamStatusChanged) {
    return isStreamEnabled ? "added" : "removed";
  } else if (streamChanged) {
    return "changed";
  }
  return "unchanged";
};

/**
 * Get change status for field: added, removed, unchanged, disabled
 * @param initialStreamNode
 * @param streamNode
 * @param field
 */
export const getFieldChangeStatus = (
  initialStreamNode: AirbyteStreamAndConfiguration,
  streamNode: SyncStreamFieldWithId,
  field?: SyncSchemaField
): Exclude<StatusToDisplay, "changed"> => {
  // if stream is disabled then disable all fields
  if (!streamNode.config?.selected) {
    return "disabled";
  }

  // don't get status for nested fields
  if (!field || SyncSchemaFieldObject.isNestedField(field)) {
    return "unchanged";
  }

  const findField = (f: SelectedFieldInfo) => isEqual(f.fieldPath, field.path);

  const fieldExistInSelectedFields = streamNode?.config?.selectedFields?.find(findField);
  const fieldExistsInSelectedFieldsInitialValue = initialStreamNode?.config?.selectedFields?.find(findField);

  // if initially field selection was enabled
  if (initialStreamNode?.config?.fieldSelectionEnabled) {
    if (streamNode?.config?.fieldSelectionEnabled) {
      if (fieldExistsInSelectedFieldsInitialValue && fieldExistInSelectedFields) {
        return "unchanged";
      }
      if (fieldExistsInSelectedFieldsInitialValue && !fieldExistInSelectedFields) {
        return "removed";
      }

      if (!fieldExistsInSelectedFieldsInitialValue && fieldExistInSelectedFields) {
        return "added";
      }

      return "unchanged";
    }

    if (!streamNode?.config?.fieldSelectionEnabled) {
      return fieldExistsInSelectedFieldsInitialValue ? "unchanged" : "added";
    }
  }

  // if initially field selection was disabled
  if (!initialStreamNode?.config?.fieldSelectionEnabled) {
    if (streamNode?.config?.fieldSelectionEnabled) {
      return fieldExistInSelectedFields ? "unchanged" : "removed";
    }
    if (!streamNode?.config?.fieldSelectionEnabled) {
      return "unchanged";
    }
  }

  return "unchanged";
};

export const getRowChangeStatus = (row: Row<SyncCatalogUIModel>) => {
  const { streamNode, initialStreamNode, field } = row.original;

  if (!initialStreamNode || !streamNode) {
    return {
      rowChangeStatus: "unchanged",
    };
  }

  const rowChangeStatus = isStreamRow(row)
    ? getStreamChangeStatus(initialStreamNode, streamNode)
    : getFieldChangeStatus(initialStreamNode, streamNode, field);

  return {
    rowChangeStatus,
  };
};
