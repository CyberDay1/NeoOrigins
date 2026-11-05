// Datapack formats from the supported Minecraft jars. The editor is schema-driven and
// does NOT translate power types between version lines — the user picks
// their target version and is responsible for sticking to power types that
// exist in that version. The choice is only used to stamp `pack.mcmeta`
// in the export bundle (see `$lib/datapack/export.ts`).

export type TargetMcVersion = '1.21.1' | '26.1' | '26.2' | '26.3';

export interface TargetVersionOption {
	id: TargetMcVersion;
	label: string;
	packFormat: number;
}

export const TARGET_VERSIONS: readonly TargetVersionOption[] = [
	{ id: '1.21.1', label: 'MC 1.21.1', packFormat: 48 },
	{ id: '26.1', label: 'MC 26.1', packFormat: 101 },
	{ id: '26.2', label: 'MC 26.2', packFormat: 107 },
	{ id: '26.3', label: 'MC 26.3', packFormat: 121 }
] as const;

export const DEFAULT_TARGET_VERSION: TargetMcVersion = '1.21.1';

export function packFormatFor(version: TargetMcVersion): number {
	return TARGET_VERSIONS.find((v) => v.id === version)?.packFormat ?? 48;
}

/** Accept format 84 from older editor exports, but re-export with 101. */
export function targetVersionForPackFormat(format: unknown): TargetMcVersion | undefined {
	return format === 84 ? '26.1' : TARGET_VERSIONS.find((v) => v.packFormat === format)?.id;
}
