export enum READER_EVENTS {
	TAG = 'TAG',
	BARCODE = 'BARCODE',
	WRITE_TAG = 'WRITE_TAG',
	TRIGGER_STATUS = 'TRIGGER_STATUS',
	READER_STATUS = 'READER_STATUS',
}

export type BatteryTypes = {
	cause: string | null;
	level: number;
};

export type DevicesTypes = {
	name: string;
	mac: string;
	antennaLevel?: string;
	power?: number;
};

export type ReaderStatus = {
	status: boolean;
	error: string;
};

export type ProgramStatus = {
	status: boolean;
	error: string;
};

export type TriggerStatus = {
	status: boolean;
};

type onReaderStatus = (data: ReaderStatus) => void;
type onTagResult = (tag: string) => void;
type onProgramResult = (data: ProgramStatus) => void;
type onTriggerStatus = (data: TriggerStatus) => void;

export type Callbacks = onReaderStatus | onTagResult | onProgramResult | onTriggerStatus;

export declare function on(event: READER_EVENTS, callback: Callbacks): void;

export declare function off(event: READER_EVENTS): void;

export declare function removeAll(event: READER_EVENTS): void;

export declare function connect(): Promise<boolean>;

export declare function reconnect(): void;

export declare function disconnect(): Promise<void>;

export declare function isConnected(): Promise<boolean>;

export declare function clear(): void;

export declare function getDeviceDetails(): Promise<DevicesTypes | null>;

export declare function setAntennaLevel(antennaLevel: number): Promise<void>;

export declare function programTag(oldTag: string, newTag: string): Promise<boolean>;

export declare function setEnabled(enable: boolean): Promise<void>;

export declare function setSingleRead(enable: boolean): void;
