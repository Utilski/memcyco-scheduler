import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SchedulingsPage } from "./features/schedulings/pages/SchedulingsPage";

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false } },
});
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SchedulingsPage />
    </QueryClientProvider>
  );
}
