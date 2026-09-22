using System.Diagnostics;
using System.Reflection;
using System.Text;
using System.Text.Json;

namespace Still.Windows;

internal record Adapter(string Id, string Name, string Link, bool Configured, bool Supported, bool CanRestore, string Dns)
{
    public override string ToString() => $"{Name} ({Link})";
}

internal static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        if (args.Length == 2 && args[0] == "--self-check")
        {
            // Read-only integration check and offscreen rendering. No DNS changes.
            var items = MainWindow.Query("Status", null).GetAwaiter().GetResult();
            using var preview = new MainWindow(diagnostic: true);
            preview.LoadAdapters(items);
            preview.ShowInTaskbar = false;
            preview.Opacity = 0;
            preview.StartPosition = FormStartPosition.Manual;
            preview.Location = new Point(-32000, -32000);
            preview.Show();
            preview.PerformLayout();
            using var bitmap = new Bitmap(preview.Width, preview.Height);
            preview.DrawToBitmap(bitmap, new Rectangle(0, 0, preview.Width, preview.Height));
            bitmap.Save(args[1]);
            Console.WriteLine($"Read-only check passed. {items.Length} physical adapters inspected.");
            return;
        }
        using var mutex = new Mutex(true, @"Local\Still.Dns.Companion", out var first);
        if (!first) { MessageBox.Show("Still is already open.", "Still"); return; }
        Application.Run(new MainWindow());
    }
}

internal sealed class MainWindow : Form
{
    readonly ComboBox adapters = new() { DropDownStyle = ComboBoxStyle.DropDownList, Dock = DockStyle.Fill, AccessibleName = "Network adapter" };
    readonly Label status = new() { AutoSize = true, MaximumSize = new Size(570, 0), Text = "Reading network settings..." };
    readonly Label dns = new() { AutoSize = true, MaximumSize = new Size(570, 0) };
    readonly Button enable = new() { Text = "Enable filtering DNS", AutoSize = true };
    readonly Button restore = new() { Text = "Restore previous DNS", AutoSize = true };
    readonly Button refresh = new() { Text = "Refresh", AutoSize = true };
    bool busy;

    public MainWindow(bool diagnostic = false)
    {
        Text = "Still | Windows DNS companion";
        MinimumSize = new Size(660, 580);
        Size = new Size(700, 640);
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Segoe UI", 11);
        BackColor = Color.FromArgb(247, 247, 245);
        var content = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true, Padding = new Padding(28) };
        Controls.Add(content);
        content.Controls.Add(new Label { Text = "Still", Font = new Font(Font.FontFamily, 30, FontStyle.Bold), AutoSize = true });
        AddText(content, "DNS filtering for this computer", true);
        AddText(content, "Select your active Wi-Fi or Ethernet adapter. Still saves its previous DNS settings before applying AdGuard Public DNS.");
        adapters.Width = 570;
        adapters.Dock = DockStyle.None;
        content.Controls.Add(adapters);
        content.Controls.Add(status);
        content.Controls.Add(dns);
        var actions = new FlowLayoutPanel { AutoSize = true, MaximumSize = new Size(590, 0), Margin = new Padding(0, 16, 0, 16) };
        actions.Controls.AddRange([enable, restore, refresh]);
        content.Controls.Add(actions);
        AddText(content, "Before you enable", true);
        AddText(content, "DNS queries from this adapter will go to AdGuard's public resolver, which applies its own ad and tracker lists. This companion does not share Android's custom rules or counters. Windows DNS encryption is not configured by this app.");
        AddText(content, "The DNS setting stays in place after you close Still or restart Windows. Use Restore previous DNS before removing the app. Restore replaces the selected adapter's current DNS settings with Still's saved copy.");
        AddText(content, "Apps with their own DNS, other adapters and VPNs may bypass filtering. YouTube video ads are not reliably blocked. Corporate or managed networks may need their original DNS to reach internal sites.");
        var privacy = new LinkLabel { Text = "AdGuard Public DNS privacy policy", AutoSize = true, Margin = new Padding(0, 12, 0, 0) };
        privacy.LinkClicked += (_, _) => Process.Start(new ProcessStartInfo("https://adguard-dns.io/en/privacy.html") { UseShellExecute = true });
        content.Controls.Add(privacy);
        adapters.SelectedIndexChanged += (_, _) => ShowSelection();
        refresh.Click += async (_, _) => await Run("Status");
        enable.Click += async (_, _) => await Run("Enable");
        restore.Click += async (_, _) => await Run("Restore");
        if (!diagnostic) Shown += async (_, _) => await Run("Status");
        FormClosing += (_, e) => { if (busy) { e.Cancel = true; status.Text = "Wait for the current DNS operation to finish before closing."; } };
    }

    void AddText(Control parent, string text, bool bold = false) => parent.Controls.Add(new Label
    {
        Text = text, AutoSize = true, MaximumSize = new Size(580, 0), Margin = new Padding(0, 8, 0, 6),
        Font = bold ? new Font(Font, FontStyle.Bold) : Font
    });

    void ShowSelection()
    {
        var item = adapters.SelectedItem as Adapter;
        enable.Enabled = !busy && item is { Link: "Up", Supported: true, CanRestore: false, Configured: false };
        restore.Enabled = !busy && item is { CanRestore: true };
        refresh.Enabled = !busy;
        adapters.Enabled = !busy;
        if (busy) return;
        status.Text = item is null ? "No physical network adapters found." : item.Configured
            ? "AdGuard DNS is configured. This is a settings check, not proof that every app is filtered."
            : item.CanRestore ? "Saved DNS settings are available to restore. Filtering DNS is not fully configured."
            : "Filtering DNS is not configured on this adapter.";
        dns.Text = item is null ? "" : "Current DNS: " + item.Dns;
    }

    async Task Run(string action)
    {
        if (busy) return;
        var id = (adapters.SelectedItem as Adapter)?.Id;
        if (action != "Status" && !Guid.TryParse(id, out _)) return;
        busy = true;
        ShowSelection();
        status.Text = action == "Status" ? "Reading network settings..." : "Applying DNS settings...";
        string? failure = null;
        try
        {
            var items = await Query(action, id);
            adapters.Items.Clear();
            adapters.Items.AddRange(items);
            adapters.SelectedItem = items.FirstOrDefault(a => a.Id == id) ?? items.FirstOrDefault(a => a.Link == "Up") ?? items.FirstOrDefault();
        }
        catch (Exception ex)
        {
            failure = ex.Message;
            // Fetch actual state after an error, including partial failure and restoration backups.
            try
            {
                var items = await Query("Status", null);
                adapters.Items.Clear();
                adapters.Items.AddRange(items);
                adapters.SelectedItem = items.FirstOrDefault(a => a.Id == id) ?? items.FirstOrDefault();
            }
            catch { adapters.Items.Clear(); }
        }
        finally { busy = false; ShowSelection(); }
        if (failure != null) MessageBox.Show(this, failure, "Still could not complete the operation", MessageBoxButtons.OK, MessageBoxIcon.Error);
    }

    internal void LoadAdapters(Adapter[] items)
    {
        adapters.Items.AddRange(items);
        adapters.SelectedItem = items.FirstOrDefault(a => a.Link == "Up") ?? items.FirstOrDefault();
        ShowSelection();
    }

    internal static async Task<Adapter[]> Query(string action, string? id)
    {
        using var source = Assembly.GetExecutingAssembly().GetManifestResourceStream("Still.Windows.DnsControl.ps1")!;
        using var reader = new StreamReader(source);
        var script = await reader.ReadToEndAsync();
        var call = "& {\n" + script + "\n} -Action " + action;
        if (id != null) call += " -AdapterId '" + Guid.Parse(id).ToString() + "'";
        var start = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), @"WindowsPowerShell\v1.0\powershell.exe"))
        {
            UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
        };
        start.ArgumentList.Add("-NoProfile");
        start.ArgumentList.Add("-NonInteractive");
        start.ArgumentList.Add("-EncodedCommand");
        start.ArgumentList.Add(Convert.ToBase64String(Encoding.Unicode.GetBytes("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; " + call)));
        using var process = Process.Start(start) ?? throw new InvalidOperationException("Could not start Windows DNS configuration.");
        var output = process.StandardOutput.ReadToEndAsync();
        var error = process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        var result = await output;
        var errorText = await error;
        if (process.ExitCode != 0) throw new InvalidOperationException(errorText.Length > 0 ? errorText : "Windows rejected the DNS change.");
        return JsonSerializer.Deserialize<Adapter[]>(result) ?? [];
    }
}
