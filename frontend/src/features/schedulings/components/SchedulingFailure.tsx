import { Button } from "../../../components/atoms/controls";
import { errorMessage } from "../../../lib/errors";
import { ApiError } from "../api/api";

export function MutationFailure({
  error,
  onReload,
}: {
  error: Error;
  onReload: () => void;
}) {
  return (
    <div role="alert" className="error">
      {errorMessage(error)}
      {error instanceof ApiError && error.status === 409 && (
        <>
          <p>
            The scheduling changed. Your draft has been kept. Reload replaces it
            with the latest version.
          </p>
          <Button onClick={onReload}>Reload latest version</Button>
        </>
      )}
    </div>
  );
}
export function RecordFailure({
  error,
  onRetry,
}: {
  error: Error;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="error">
      {errorMessage(error)}
      <div className="dialog-footer">
        <Button onClick={onRetry}>
          {error instanceof ApiError && error.status === 404
            ? "Close and refresh"
            : "Retry"}
        </Button>
      </div>
    </div>
  );
}
