export interface AudioDeviceStatus {
  hasEarbudsConnected: boolean;
  deviceName: string;
  isSimulatedOverride: boolean;
  reason?: string;
}

type DeviceChangeCallback = (status: AudioDeviceStatus) => void;

class EarbudsAudioGuardService {
  private simulatedConnected: boolean = true; // Default simulated connected for immediate usability
  private simulatedDeviceName: string = 'AirPods Pro (Bluetooth)';
  private listeners: Set<DeviceChangeCallback> = new Set();
  private realDevicesDetected: boolean = false;

  constructor() {
    this.initHardwareDetection();
  }

  private async initHardwareDetection() {
    if (typeof navigator !== 'undefined' && navigator.mediaDevices) {
      try {
        navigator.mediaDevices.addEventListener('devicechange', () => {
          this.checkRealDevices();
        });
        await this.checkRealDevices();
      } catch (err) {
        console.warn('Media devices enumeration not supported or permission denied', err);
      }
    }
  }

  public async checkRealDevices(): Promise<AudioDeviceStatus> {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.enumerateDevices) {
      return this.getStatus();
    }

    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const audioOutputs = devices.filter(d => d.kind === 'audiooutput');
      
      const bluetoothOrHeadphones = audioOutputs.find(d => {
        const label = d.label.toLowerCase();
        return (
          label.includes('bluetooth') ||
          label.includes('airpods') ||
          label.includes('headphone') ||
          label.includes('headset') ||
          label.includes('buds') ||
          label.includes('ear') ||
          label.includes('wireless')
        );
      });

      if (bluetoothOrHeadphones && bluetoothOrHeadphones.label) {
        this.realDevicesDetected = true;
        this.simulatedDeviceName = bluetoothOrHeadphones.label;
        this.simulatedConnected = true;
      }
    } catch {
      // Permission not yet granted for device labels
    }

    const status = this.getStatus();
    this.notify();
    return status;
  }

  public getStatus(): AudioDeviceStatus {
    return {
      hasEarbudsConnected: this.simulatedConnected,
      deviceName: this.simulatedConnected ? this.simulatedDeviceName : 'Phone Speaker (Restricted)',
      isSimulatedOverride: true,
      reason: this.simulatedConnected
        ? 'Bluetooth Earbuds Active. Safe for private playback.'
        : 'Privacy Guard Active: No earbuds connected. Audio blocked from phone speaker.'
    };
  }

  public setSimulatedConnection(connected: boolean, deviceName: string = 'AirPods Pro (Bluetooth)') {
    this.simulatedConnected = connected;
    this.simulatedDeviceName = deviceName;
    this.notify();
  }

  public subscribe(callback: DeviceChangeCallback): () => void {
    this.listeners.add(callback);
    callback(this.getStatus());
    return () => {
      this.listeners.delete(callback);
    };
  }

  private notify() {
    const status = this.getStatus();
    this.listeners.forEach(cb => cb(status));
  }
}

export const EarbudsGuard = new EarbudsAudioGuardService();
