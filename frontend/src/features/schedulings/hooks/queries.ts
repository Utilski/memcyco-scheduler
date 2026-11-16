import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { listSchedulings, loadTaskTypes, mutateScheduling } from "../api/api";
import type { ListQuery } from "../model/types";

export function useSchedulings(query: ListQuery) {
  return useQuery({
    queryKey: ["schedulings", query],
    queryFn: ({ signal }) => listSchedulings(query, signal),
    placeholderData: keepPreviousData,
    refetchInterval: 10_000,
    retry: 1,
  });
}
export function useTaskTypes() {
  return useQuery({
    queryKey: ["task-types"],
    queryFn: ({ signal }) => loadTaskTypes(signal),
    staleTime: 60_000,
    retry: 1,
  });
}
export function useSchedulingMutation() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: mutateScheduling,
    onSuccess: () => client.invalidateQueries({ queryKey: ["schedulings"] }),
  });
}
