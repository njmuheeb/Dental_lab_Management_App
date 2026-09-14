/**
 * Dental Lab Management - Google Sheets Sync (Apps Script Web App)
 * ----------------------------------------------------------------
 * OPTIONAL mirror of the app's offline Room database into your own Google Sheet.
 * The Android app remains the source of truth; this script only receives pushed rows.
 *
 * SETUP
 *  1. Create a new Google Sheet (any name, e.g. "Dental Lab Management Backup").
 *  2. Extensions > Apps Script, delete the sample code and paste this file.
 *  3. Set the SECRET constant below (any long random string).
 *  4. Deploy > New deployment > type "Web app":
 *        - Execute as: Me
 *        - Who has access: Anyone
 *     Copy the /exec URL.
 *  5. In the Android app: Settings > Google Sheets Sync > enable, paste the URL and the
 *     same SECRET, then tap "Sync Now".
 *
 * DEDUPLICATION: rows are matched by the unique `syncId` column - re-syncing never
 * duplicates entries; it updates matching rows in place. Deleted rows are removed
 * from the sheet via the deletes list.
 */

var SECRET = 'CHANGE_ME_TO_A_LONG_RANDOM_SECRET';

var ENTITY_SHEETS = {
  clinic: 'Clinics',
  patient: 'Patients',
  work_type: 'Work Types',
  clinic_rate: 'Clinic Rates',
  work_order: 'Work Orders',
  payment: 'Payments'
};

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    if (SECRET && SECRET !== 'CHANGE_ME_TO_A_LONG_RANDOM_SECRET' && body.secret !== SECRET) {
      return json_({ status: 'error', message: 'unauthorized' });
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var processed = 0;

    // 1. Apply deletes (tombstones)
    (body.deletes || []).forEach(function (del) {
      var sheetName = ENTITY_SHEETS[del.entityType];
      if (!sheetName) return;
      var sheet = ss.getSheetByName(sheetName);
      if (!sheet || sheet.getLastRow() < 2) return;
      var syncIdCol = findSyncIdColumn_(sheet);
      if (syncIdCol < 0) return;
      var values = sheet.getRange(2, 1, sheet.getLastRow() - 1, syncIdCol).getValues();
      for (var r = values.length - 1; r >= 0; r--) {
        if (String(values[r][syncIdCol - 1]) === String(del.syncId)) {
          sheet.deleteRow(r + 2);
          processed++;
        }
      }
    });

    // 2. Upsert entity rows by syncId
    var upserts = [
      ['clinic', body.clinics],
      ['patient', body.patients],
      ['work_type', body.workTypes],
      ['clinic_rate', body.clinicRates],
      ['work_order', body.workOrders],
      ['payment', body.payments]
    ];

    upserts.forEach(function (pair) {
      var entityType = pair[0];
      var rows = pair[1] || [];
      if (rows.length === 0) return;
      var sheetName = ENTITY_SHEETS[entityType];
      var sheet = ss.getSheetByName(sheetName) || ss.insertSheet(sheetName);

      if (sheet.getLastRow() === 0) {
        // First data ever: write header from the first row's keys (stable order = JSON order)
        var headers = Object.keys(rows[0]);
        sheet.appendRow(headers);
      }

      var headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      var syncIdCol = findSyncIdColumn_(sheet);
      if (syncIdCol < 0) {
        // syncId column missing (schema changed) - rebuild headers from current payload
        headers = Object.keys(rows[0]);
        sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
        syncIdCol = headers.indexOf('syncId') + 1;
      }

      // Build an index of existing syncIds -> row number
      var existing = {};
      if (sheet.getLastRow() > 1) {
        var existingIds = sheet.getRange(2, syncIdCol, sheet.getLastRow() - 1, 1).getValues();
        for (var i = 0; i < existingIds.length; i++) {
          existing[String(existingIds[i][0])] = i + 2;
        }
      }

      rows.forEach(function (row) {
        var values = headers.map(function (h) {
          var v = row[h];
          return v === undefined || v === null ? '' : v;
        });
        var rowNum = existing[String(row.syncId)];
        if (rowNum) {
          sheet.getRange(rowNum, 1, 1, headers.length).setValues([values]);
        } else {
          sheet.appendRow(values);
          existing[String(row.syncId)] = sheet.getLastRow();
        }
        processed++;
      });
    });

    return json_({ status: 'ok', processed: processed });
  } catch (err) {
    return json_({ status: 'error', message: String(err) });
  }
}

function doGet() {
  return json_({ status: 'ok', message: 'Dental Lab Management sync endpoint is alive. Use POST.' });
}

function findSyncIdColumn_(sheet) {
  if (sheet.getLastColumn() === 0) return -1;
  var headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  var idx = headers.indexOf('syncId');
  return idx + 1; // 1-based; 0 when not found
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
