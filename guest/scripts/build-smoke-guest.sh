#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
guest_dir=$(cd -- "$script_dir/.." && pwd)
profile="$guest_dir/kernel/alpine-3.24.2-x86_64.properties"
artifact_dir="$guest_dir/artifacts/alpine-3.24.2-x86_64-smoke"
cache_dir="$guest_dir/cache"

for tool in curl sha256sum busybox cpio gzip; do
    command -v "$tool" >/dev/null || {
        echo "Required tool is missing: $tool" >&2
        exit 1
    }
done

# shellcheck disable=SC1090
source "$profile"
mkdir -p "$artifact_dir" "$cache_dir"
kernel_cache="$cache_dir/$filename"

if [[ ! -f "$kernel_cache" ]] || [[ "$(sha256sum "$kernel_cache" | cut -d' ' -f1)" != "$sha256" ]]; then
    partial="$kernel_cache.partial"
    rm -f "$partial"
    curl --fail --location --retry 3 --output "$partial" "$url"
    echo "$sha256  $partial" | sha256sum --check --status
    mv "$partial" "$kernel_cache"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/root/bin" "$work/root/dev" "$work/root/proc" "$work/root/sys"
busybox_path=$(command -v busybox)
if ldd "$busybox_path" 2>&1 | grep -qv 'not a dynamic executable'; then
    echo "The initramfs requires a statically linked busybox binary" >&2
    exit 1
fi
cp "$busybox_path" "$work/root/bin/busybox"
cp "$guest_dir/initramfs/overlay/init" "$work/root/init"
chmod 0755 "$work/root/init"

(cd "$work/root" && find . -print0 | cpio --null -o --format=newc 2>/dev/null \
    | gzip -9n > "$artifact_dir/initramfs.cpio.gz")
cp "$kernel_cache" "$artifact_dir/vmlinuz"
(
    cd "$artifact_dir"
    sha256sum vmlinuz initramfs.cpio.gz > SHA256SUMS
)

echo "Guest artifacts created in $artifact_dir"
echo "Run:"
echo "  ./gradlew :cli:installDist"
echo "  ./cli/build/install/panama-kvm/bin/panama-kvm --kernel $artifact_dir/vmlinuz --initrd $artifact_dir/initramfs.cpio.gz --non-interactive"
