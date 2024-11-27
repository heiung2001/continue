/**
 * This is the entry point for the extension.
 */

import { setupCa } from "core/util/ca";
import { Telemetry } from "core/util/posthog";
import * as vscode from "vscode";
import { getExtensionVersion } from "./util/util";
import { fetchwithRequestOptions } from "core/util/fetchWithOptions";
import { getPlatform } from "./util/util";

async function dynamicImportAndActivate(context: vscode.ExtensionContext) {
  const { activateExtension } = await import("./activation/activate");
  try {
    return activateExtension(context);
  } catch (e) {
    console.log("Error activating extension: ", e);
    vscode.window
      .showInformationMessage(
        "Error activating the Continue extension.",
        "View Logs",
        "Retry",
      )
      .then((selection) => {
        if (selection === "View Logs") {
          vscode.commands.executeCommand("continue.viewLogs");
        } else if (selection === "Retry") {
          // Reload VS Code window
          vscode.commands.executeCommand("workbench.action.reloadWindow");
        }
      });
  }
}

export async function activate(context: vscode.ExtensionContext) {
  setupCa();
  
  const currentVersion = getExtensionVersion();
  const os = getPlatform();
  const ide = "vscode";

  let isLatest: boolean = true;
  let latestVersion: string | null = null;
  let downloadLink: string | null = null;

  try {
    const url = `http://10.30.132.71:10011/api/version/ext/?ide=${ide}&os_name=${os}`;
    const response = await fetchwithRequestOptions(url);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch version info: ${response.statusText}`);
    };

    const data: any = await response.json();
    ({ latest_version: latestVersion, download_link: downloadLink } = data);
    isLatest = currentVersion === latestVersion;
    
  } catch (e) {
    isLatest = true;
    latestVersion = null;
    downloadLink = null;
  }

  if (!isLatest && latestVersion && downloadLink) {
    vscode.window.showInformationMessage(
      `CoDev update available. Your current version is ${currentVersion} but the latest version is ${latestVersion}`,
      'Download Now',
      'Ignore'
    ).then((selection) => {
      if (selection == "Download Now" && downloadLink) {
        vscode.env.openExternal(vscode.Uri.parse(downloadLink));
      }
    });
  }

  return dynamicImportAndActivate(context);
}

export function deactivate() {
  Telemetry.capture(
    "deactivate",
    {
      extensionVersion: getExtensionVersion(),
    },
    true,
  );

  Telemetry.shutdownPosthogClient();
}
