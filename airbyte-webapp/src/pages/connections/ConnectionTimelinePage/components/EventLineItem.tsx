import { InferType } from "yup";

import { Box } from "components/ui/Box";

import { ConnectionEvent } from "core/api/types/AirbyteClient";

import { ClearEventItem } from "./ClearEventItem";
import { ConnectionDisabledEventItem } from "./ConnectionDisabledEventItem";
import { ConnectionEnabledEventItem } from "./ConnectionEnabledEventItem";
import { ConnectionSettingsUpdateEventItem } from "./ConnectionSettingsUpdateEventItem";
import { JobStartEventItem } from "./JobStartEventItem";
import { RefreshEventItem } from "./RefreshEventItem";
import { RunningJobItem } from "./RunningJobItem";
import { SyncEventItem } from "./SyncEventItem";
import { SyncFailEventItem } from "./SyncFailEventItem";
import {
  clearEventSchema,
  syncFailEventSchema,
  jobStartedEventSchema,
  refreshEventSchema,
  syncEventSchema,
  jobRunningSchema,
  connectionEnabledEventSchema,
  connectionDisabledEventSchema,
  connectionSettingsUpdateEventSchema,
} from "../types";

export const EventLineItem: React.FC<{ event: ConnectionEvent | InferType<typeof jobRunningSchema> }> = ({ event }) => {
  if (jobRunningSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <RunningJobItem jobRunningItem={event} />
      </Box>
    );
  } else if (syncEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <SyncEventItem syncEvent={event} />
      </Box>
    );
  } else if (syncFailEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <SyncFailEventItem syncEvent={event} />
      </Box>
    );
  } else if (refreshEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <RefreshEventItem refreshEvent={event} />
      </Box>
    );
  } else if (clearEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <ClearEventItem clearEvent={event} />
      </Box>
    );
  } else if (jobStartedEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <JobStartEventItem jobStartEvent={event} />
      </Box>
    );
  } else if (connectionEnabledEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <ConnectionEnabledEventItem event={event} />
      </Box>
    );
  } else if (connectionDisabledEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <ConnectionDisabledEventItem event={event} />
      </Box>
    );
  } else if (connectionSettingsUpdateEventSchema.isValidSync(event, { recursive: true, stripUnknown: true })) {
    return (
      <Box py="lg" key={event.id}>
        <ConnectionSettingsUpdateEventItem event={event} />
      </Box>
    );
  }
  return null;
};
