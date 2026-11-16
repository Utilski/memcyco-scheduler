import { Field } from "../../../components/molecules/Field";
import { Input, Select } from "../../../components/atoms/controls";
import { humanize } from "../utils/format";
import { scheduleTypeSchema } from "../model/types";
import type { FormValues } from "../model/form";
import type { UpdateForm } from "../model/form";

const zones = ["UTC", ...Intl.supportedValuesOf("timeZone")];
export function TimingFields({
  form,
  update,
}: {
  form: FormValues;
  update: UpdateForm;
}) {
  return (
    <>
      <Field label="Schedule type">
        <Select
          value={form.scheduleType}
          onChange={(event) => {
            const type = scheduleTypeSchema.parse(event.target.value);
            update("scheduleType", type);
            update("unit", type === "CALENDAR_INTERVAL" ? "DAY" : "SECONDS");
            if (type === "ONE_TIME") update("maxOccurrences", "");
          }}
        >
          {scheduleTypeSchema.options.map((type) => (
            <option key={type} value={type}>
              {humanize(type)}
            </option>
          ))}
        </Select>
      </Field>
      {(form.scheduleType === "SIMPLE_INTERVAL" ||
        form.scheduleType === "CALENDAR_INTERVAL") && (
        <div className="form-grid">
          <Field label="Interval">
            <Input
              type="number"
              min="1"
              max="2147483647"
              step="1"
              required
              value={form.interval}
              onChange={(event) => update("interval", event.target.value)}
            />
          </Field>
          <Field label="Interval unit">
            <Select
              value={form.unit}
              onChange={(event) => update("unit", event.target.value)}
            >
              {(form.scheduleType === "SIMPLE_INTERVAL"
                ? ["SECONDS", "MINUTES", "HOURS"]
                : ["DAY", "WEEK", "MONTH", "YEAR"]
              ).map((unit) => (
                <option key={unit}>{unit}</option>
              ))}
            </Select>
          </Field>
        </div>
      )}
      {form.scheduleType === "CRON" && (
        <Field
          label="Quartz cron expression"
          hint="Seconds first; use ? for an unspecified day. Example: 0 0 9 ? * MON-FRI"
        >
          <Input
            required
            className="mono"
            value={form.expression}
            onChange={(event) => update("expression", event.target.value)}
          />
        </Field>
      )}
      <div className="form-grid">
        <Field label="Timezone (IANA)">
          <Input
            required
            list="timezone-options"
            value={form.timeZone}
            onChange={(event) => update("timeZone", event.target.value)}
          />
          <datalist id="timezone-options">
            {zones.map((zone) => (
              <option key={zone} value={zone} />
            ))}
          </datalist>
        </Field>
        <Field label="Start time (in selected timezone)">
          <Input
            type="datetime-local"
            step="0.001"
            required
            value={form.startsAt}
            onChange={(event) => update("startsAt", event.target.value)}
          />
        </Field>
        <Field label="End time (UTC, optional)">
          <Input
            type="datetime-local"
            step="0.001"
            value={form.endsAt}
            onChange={(event) => update("endsAt", event.target.value)}
          />
        </Field>
        {form.scheduleType !== "ONE_TIME" && (
          <Field label="Maximum occurrences (optional)">
            <Input
              type="number"
              min="1"
              max={Number.MAX_SAFE_INTEGER}
              step="1"
              value={form.maxOccurrences}
              onChange={(event) => update("maxOccurrences", event.target.value)}
            />
          </Field>
        )}
      </div>
    </>
  );
}
