import posthog from "posthog-js";
import { PostHogProvider } from "posthog-js/react";
import React, { PropsWithChildren, useEffect } from "react";
import { useSelector } from "react-redux";
import { RootState } from "../redux/store";

const CustomPostHogProvider = ({ children }: PropsWithChildren) => {
  const allowAnonymousTelemetry = useSelector(
    (store: RootState) => store?.state?.config.allowAnonymousTelemetry,
  );

  const [client, setClient] = React.useState<any>(undefined);

  useEffect(() => {
    if (allowAnonymousTelemetry) {
      posthog.init("phc_TkxWhhysirPKfLfIiCrQUI8mhte52yRpobi5rBNx8I4", {
        api_host: "http://10.30.132.71:9025",
        disable_session_recording: true,
        autocapture: false,
        // // We need to manually track pageviews since we're a SPA
        capture_pageleave: false,
        capture_pageview: false,
      });
      posthog.identify(window.vscMachineId);
      posthog.opt_in_capturing();
      setClient(client);
    } else {
      setClient(undefined);
    }
  }, [allowAnonymousTelemetry]);

  return allowAnonymousTelemetry ? (
    <PostHogProvider client={client}>{children}</PostHogProvider>
  ) : (
    <>{children}</>
  );
};

export default CustomPostHogProvider;
